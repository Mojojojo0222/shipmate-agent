package com.shipmate.service;

import com.shipmate.config.AgentProperties;
import com.shipmate.git.GitHubClient;
import com.shipmate.k8s.K8sService;
import com.shipmate.model.App;
import com.shipmate.model.DeploymentRun;
import com.shipmate.model.RunState;
import com.shipmate.repository.AppRepository;
import com.shipmate.repository.DeploymentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DeployEngine {

    private static final Logger log = LoggerFactory.getLogger(DeployEngine.class);

    private final DeploymentRunRepository runRepo;
    private final AppRepository appRepo;
    private final GitHubClient gitHub;
    private final K8sService k8s;
    private final RollbackService rollbackService;
    private final HealthService healthService;
    private final AuditService audit;
    private final AgentProperties props;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DeployEngine(DeploymentRunRepository runRepo, AppRepository appRepo, GitHubClient gitHub,
                        K8sService k8s, RollbackService rollbackService, HealthService healthService,
                        AuditService audit, AgentProperties props) {
        this.runRepo = runRepo;
        this.appRepo = appRepo;
        this.gitHub = gitHub;
        this.k8s = k8s;
        this.rollbackService = rollbackService;
        this.healthService = healthService;
        this.audit = audit;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "10000")
    @Transactional
    public void claimPendingRuns() {
        var pending = runRepo.findByStateOrderByIdAsc(RunState.PENDING);
        for (DeploymentRun run : pending) {
            if (runRepo.claim(run.getId(), RunState.TRIGGERING, RunState.PENDING) == 1) {
                executor.submit(() -> execute(run.getId()));
            }
        }
    }

    private void execute(Long runId) {
        DeploymentRun run = runRepo.findById(runId).orElse(null);
        if (run == null) return;
        App app = appRepo.findById(run.getAppId()).orElse(null);
        if (app == null) {
            fail(run, "app not found");
            return;
        }
        try {
            app.setStatus(com.shipmate.model.AppStatus.DEPLOYING);
            appRepo.save(app);
            audit.log("engine", "run.start", "run=" + run.getId() + " app=" + app.getName() + " sha=" + run.getCommitSha());

            if (!triggerCi(run, app)) return;

            if (!waitForCi(run, app)) return;

            if (!waitForDeploy(run, app)) return;

            if (!verifyHealth(run, app)) return;

            finishHealthy(run, app);
        } catch (Exception e) {
            log.error("pipeline failed for run {}", run.getId(), e);
            fail(run, e.getMessage());
        }
    }

    private boolean triggerCi(DeploymentRun run, App app) throws InterruptedException {
        setState(run, RunState.TRIGGERING);
        boolean ok = gitHub.dispatchWorkflow(app.getRepoOwner(), app.getRepoName(), app.getWorkflowFile(), app.getBranch());
        if (!ok) {
            fail(run, "failed to dispatch workflow " + app.getWorkflowFile());
            return false;
        }
        audit.log("engine", "ci.dispatch", "app=" + app.getName() + " workflow=" + app.getWorkflowFile());
        return true;
    }

    private boolean waitForCi(DeploymentRun run, App app) throws InterruptedException {
        setState(run, RunState.CI_RUNNING);
        long deadline = System.currentTimeMillis() + props.getPipeline().getCiTimeoutMinutes() * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                var status = gitHub.getRunStatus(app.getRepoOwner(), app.getRepoName(), run.getCommitSha());
                run.setWorkflowRunUrl(status.htmlUrl());
                if ("completed".equals(status.status())) {
                    if ("success".equals(status.conclusion())) {
                        audit.log("engine", "ci.completed", "app=" + app.getName() + " conclusion=success");
                        return true;
                    }
                    fail(run, "CI failed: " + status.conclusion());
                    return false;
                }
            } catch (Exception e) {
                log.warn("ci poll error: {}", e.getMessage());
            }
            Thread.sleep(15_000);
        }
        fail(run, "CI timeout after " + props.getPipeline().getCiTimeoutMinutes() + " min");
        return false;
    }

    private boolean waitForDeploy(DeploymentRun run, App app) throws InterruptedException {
        setState(run, RunState.DEPLOYING);
        audit.log("engine", "deploy.waiting", "app=" + app.getName() + " tag=" + run.getCommitSha());
        boolean rolled = k8s.waitForDeploymentImage(app.getNamespace(), app.getName(), run.getCommitSha(),
                props.getPipeline().getDeployTimeoutMinutes());
        if (!rolled) {
            fail(run, "deployment did not roll out in " + props.getPipeline().getDeployTimeoutMinutes() + " min");
            return false;
        }
        return true;
    }

    private boolean verifyHealth(DeploymentRun run, App app) throws InterruptedException {
        setState(run, RunState.VERIFYING);
        int retries = props.getPipeline().getHealthRetries();
        for (int i = 0; i < retries; i++) {
            if (healthService.checkNow(app).isUp()) {
                audit.log("engine", "verify.healthy", "app=" + app.getName() + " attempt=" + (i + 1));
                return true;
            }
            Thread.sleep(props.getPipeline().getHealthIntervalSeconds() * 1000L);
        }
        return handleRollback(run, app);
    }

    private boolean handleRollback(DeploymentRun run, App app) throws InterruptedException {
        setState(run, RunState.UNHEALTHY);
        audit.log("engine", "verify.unhealthy", "app=" + app.getName() + " sha=" + run.getCommitSha());

        if (!props.getPipeline().isRollbackEnabled() || run.getRollbackAttempts() >= props.getPipeline().getMaxRollbacks()) {
            fail(run, "health check failed and rollback exhausted");
            return false;
        }

        setState(run, RunState.ROLLING_BACK);
        String previousTag = rollbackService.rollbackToPrevious(app, run.getCommitSha());
        if (previousTag == null) {
            fail(run, "no previous good image found for rollback");
            return false;
        }
        audit.log("engine", "rollback.executed", "app=" + app.getName() + " from=" + run.getCommitSha() + " to=" + previousTag);
        run.setRollbackAttempts(run.getRollbackAttempts() + 1);
        runRepo.save(run);

        boolean rolled = k8s.waitForDeploymentImage(app.getNamespace(), app.getName(), previousTag,
                props.getPipeline().getDeployTimeoutMinutes());
        if (!rolled) {
            fail(run, "rollback rollout did not complete");
            return false;
        }

        int retries = props.getPipeline().getHealthRetries();
        for (int i = 0; i < retries; i++) {
            if (healthService.checkNow(app).isUp()) {
                finishHealthy(run, app, true);
                return false;
            }
            Thread.sleep(props.getPipeline().getHealthIntervalSeconds() * 1000L);
        }
        fail(run, "rollback deployed but app still unhealthy");
        return false;
    }

    private void finishHealthy(DeploymentRun run, App app) {
        finishHealthy(run, app, false);
    }

    private void finishHealthy(DeploymentRun run, App app, boolean rolledBack) {
        setState(run, rolledBack ? RunState.ROLLED_BACK : RunState.HEALTHY);
        app.setLastImageTag(run.getCommitSha());
        app.setStatus(rolledBack ? com.shipmate.model.AppStatus.RUNNING : com.shipmate.model.AppStatus.RUNNING);
        String endpoint = k8s.getServiceEndpoint(app.getNamespace(), app.getName());
        if (endpoint != null) {
            app.setDeployedUrl(endpoint);
        }
        appRepo.save(app);
        audit.log("engine", "run.finished", "app=" + app.getName() + " state=" + run.getState() + " url=" + app.getDeployedUrl());
    }

    private void setState(DeploymentRun run, RunState state) {
        run.setState(state);
        if (state == RunState.HEALTHY || state == RunState.FAILED || state == RunState.ROLLED_BACK) {
            run.setFinishedAt(LocalDateTime.now());
        }
        runRepo.save(run);
    }

    private void fail(DeploymentRun run, String error) {
        run.setError(error);
        run.setState(RunState.FAILED);
        run.setFinishedAt(LocalDateTime.now());
        runRepo.save(run);
        Optional<App> app = appRepo.findById(run.getAppId());
        app.ifPresent(a -> {
            a.setStatus(com.shipmate.model.AppStatus.FAILED);
            appRepo.save(a);
        });
        audit.log("engine", "run.failed", "run=" + run.getId() + " error=" + error);
    }
}
