package com.shipmate.service;

import com.shipmate.config.AgentProperties;
import com.shipmate.git.GitHubClient;
import com.shipmate.model.App;
import com.shipmate.model.AppStatus;
import com.shipmate.model.DeploymentRun;
import com.shipmate.model.TriggerType;
import com.shipmate.repository.AppRepository;
import com.shipmate.repository.DeploymentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    private final AppRepository appRepo;
    private final DeploymentRunRepository runRepo;
    private final GitHubClient gitHub;
    private final CostEstimator costEstimator;
    private final SpecGenerator specGenerator;
    private final ManifestGenerator manifestGenerator;
    private final AgentProperties props;
    private final AuditService audit;
    private final com.shipmate.k8s.K8sService k8s;

    public AppService(AppRepository appRepo, DeploymentRunRepository runRepo, GitHubClient gitHub,
                      CostEstimator costEstimator, SpecGenerator specGenerator, ManifestGenerator manifestGenerator,
                      AgentProperties props, AuditService audit, com.shipmate.k8s.K8sService k8s) {
        this.appRepo = appRepo;
        this.runRepo = runRepo;
        this.gitHub = gitHub;
        this.costEstimator = costEstimator;
        this.specGenerator = specGenerator;
        this.manifestGenerator = manifestGenerator;
        this.props = props;
        this.audit = audit;
        this.k8s = k8s;
    }

    public record RegisterRequest(String repoUrl, int trafficRpm, int trafficUsers, Integer containerPort,
                                  String healthPath, String dbEngine) {}

    @Transactional
    public App register(RegisterRequest req) {
        String[] parts = parseRepoUrl(req.repoUrl());
        String owner = parts[0];
        String repo = parts[1];

        if (!gitHub.repoExists(owner, repo)) {
            throw new IllegalArgumentException("repo " + owner + "/" + repo + " not found or not accessible");
        }
        if (!gitHub.fileExists(owner, repo, "Dockerfile", "main")
                && !gitHub.fileExists(owner, repo, "Dockerfile", "master")) {
            throw new IllegalArgumentException("no Dockerfile found in repo — Shipmate needs a containerized app");
        }

        Optional<App> existing = appRepo.findByRepoOwnerAndRepoName(owner, repo);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("app already registered as " + existing.get().getName());
        }

        App app = new App();
        app.setName(repo.toLowerCase().replaceAll("[^a-z0-9-]", "-"));
        app.setRepoUrl("https://github.com/" + owner + "/" + repo + ".git");
        app.setRepoOwner(owner);
        app.setRepoName(repo);
        app.setBranch(gitHub.getDefaultBranch(owner, repo));
        app.setTrafficRpm(req.trafficRpm());
        app.setTrafficUsers(req.trafficUsers());
        app.setContainerPort(req.containerPort() == null ? 8080 : req.containerPort());
        app.setDbEngine(req.dbEngine() == null || req.dbEngine().isEmpty() ? "postgres" : req.dbEngine().toLowerCase());
        app.setNamespace(props.getK8s().getNamespacePrefix() + "-" + app.getName());
        app.setWorkflowFile(props.getGithub().getCiWorkflow());
        app.setHealthUrl("http://" + app.getName() + "." + app.getNamespace() + ".svc.cluster.local:"
                + app.getContainerPort() + (req.healthPath() == null || req.healthPath().isEmpty()
                ? "/actuator/health" : req.healthPath()));
        app.setValuesPath(props.getGitops().getManifestsPath() + "/" + app.getName() + "/helm/values.yaml");
        app.setStatus(AppStatus.DRAFT);
        App saved = appRepo.save(app);
        audit.log("user", "app.register", "app=" + saved.getName() + " repo=" + owner + "/" + repo);
        return saved;
    }

    public Map<String, Object> estimate(Long appId) {
        App app = get(appId);
        var sizing = costEstimator.computeSizing(app.getTrafficRpm());
        var breakdown = costEstimator.breakdown(app.getTrafficRpm());
        double total = breakdown.stream().mapToDouble(i -> (double) i.get("monthly")).sum();
        app.setCostBreakdownJson(costEstimator.toJson(breakdown));
        app.setEstimatedMonthlyCost(total);
        app.setSpecYaml(specGenerator.generate(app, sizing, total));
        app.setStatus(AppStatus.ESTIMATED);
        appRepo.save(app);
        audit.log("user", "app.estimate", "app=" + app.getName() + " total=$" + total);
        return Map.of(
                "app", app,
                "sizing", sizing,
                "breakdown", breakdown,
                "totalMonthly", total,
                "specYaml", app.getSpecYaml());
    }

    @Transactional
    public DeploymentRun deploy(Long appId) {
        App app = get(appId);

        var helm = manifestGenerator.generateHelmChartWithDb(app, costEstimator.computeSizing(app.getTrafficRpm()));
        String argoYaml = null;
        try {
            commitFiles(app, helm);
            String argoPath = props.getGitops().getManifestsPath() + "/apps/" + app.getName() + ".yaml";
            argoYaml = manifestGenerator.generateArgoApp(app);
            commitFile(app, argoPath, argoYaml);
            String wfPath = ".github/workflows/" + app.getWorkflowFile();
            commitFile(app, wfPath, manifestGenerator.generateCiWorkflow(app));
        } catch (Exception e) {
            throw new IllegalStateException("failed to write manifests to repo: " + e.getMessage(), e);
        }

        if (argoYaml != null && k8s.applyYaml(argoYaml)) {
            audit.log("engine", "argocd.application.applied", "app=" + app.getName());
        } else {
            throw new IllegalStateException("failed to register app with ArgoCD");
        }

        app.setStatus(AppStatus.DEPLOYING);
        appRepo.save(app);

        String headSha = gitHub.getHeadSha(app.getRepoOwner(), app.getRepoName(), app.getBranch());

        DeploymentRun run = new DeploymentRun();
        run.setAppId(app.getId());
        run.setAppName(app.getName());
        run.setBranch(app.getBranch());
        run.setTrigger(TriggerType.WIZARD);
        run.setCommitSha(headSha);
        run.setImageTag(headSha);
        run.setCommitMessage("shipmate wizard deploy");
        DeploymentRun saved = runRepo.save(run);
        audit.log("user", "app.deploy", "app=" + app.getName() + " run=" + saved.getId() + " sha=" + headSha);
        return saved;
    }

    private void commitFiles(App app, Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> e : files.entrySet()) {
            commitFile(app, e.getKey(), e.getValue());
        }
    }

    private void commitFile(App app, String path, String content) throws Exception {
        try {
            var existing = gitHub.getFile(app.getRepoOwner(), app.getRepoName(), path, app.getBranch());
            gitHub.putFile(app.getRepoOwner(), app.getRepoName(), path,
                    "shipmate: update " + path + " [skip ci]", content, existing.sha(), app.getBranch());
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("404")) {
                gitHub.putFile(app.getRepoOwner(), app.getRepoName(), path,
                        "shipmate: create " + path + " [skip ci]", content, null, app.getBranch());
            } else {
                throw ex;
            }
        }
    }

    public App get(Long id) {
        return appRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("app not found: " + id));
    }

    public List<App> list() {
        return appRepo.findAll();
    }

    public void remove(Long id) {
        App app = get(id);
        app.setStatus(AppStatus.PAUSED);
        appRepo.save(app);
        audit.log("user", "app.remove", "app=" + app.getName());
    }

    public App update(Long id, Map<String, Object> body) {
        App app = get(id);
        if (body.containsKey("trafficRpm")) {
            app.setTrafficRpm(((Number) body.get("trafficRpm")).intValue());
        }
        if (body.containsKey("trafficUsers")) {
            app.setTrafficUsers(((Number) body.get("trafficUsers")).intValue());
        }
        if (body.containsKey("healthPath")) {
            app.setHealthUrl("http://" + app.getName() + "." + app.getNamespace() + ".svc.cluster.local:"
                    + app.getContainerPort() + body.get("healthPath"));
        }
        app.setStatus(AppStatus.ESTIMATED);
        appRepo.save(app);
        audit.log("user", "app.update", "app=" + app.getName() + " trafficRpm=" + app.getTrafficRpm());
        return app;
    }

    public static String[] parseRepoUrl(String url) {
        String u = url.trim();
        if (u.endsWith(".git")) {
            u = u.substring(0, u.length() - 4);
        }
        if (u.contains("github.com/")) {
            u = u.substring(u.indexOf("github.com/") + "github.com/".length());
        }
        String[] parts = u.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("repo URL must look like https://github.com/owner/repo or owner/repo");
        }
        return new String[]{parts[0].trim(), parts[1].trim()};
    }
}
