package com.shipmate.service;

import com.shipmate.config.AgentProperties;
import com.shipmate.git.GitHubClient;
import com.shipmate.model.App;
import com.shipmate.model.DeploymentRun;
import com.shipmate.model.TriggerType;
import com.shipmate.repository.AppRepository;
import com.shipmate.repository.DeploymentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CommitPoller {

    private static final Logger log = LoggerFactory.getLogger(CommitPoller.class);

    private final AgentProperties props;
    private final AppRepository appRepo;
    private final DeploymentRunRepository runRepo;
    private final GitHubClient gitHub;

    public CommitPoller(AgentProperties props, AppRepository appRepo, DeploymentRunRepository runRepo, GitHubClient gitHub) {
        this.props = props;
        this.appRepo = appRepo;
        this.runRepo = runRepo;
        this.gitHub = gitHub;
    }

    @Scheduled(fixedDelayString = "${shipmate.github.poll-interval-sec}000")
    public void poll() {
        if (!props.getGithub().isPollEnabled()) {
            return;
        }
        for (App app : appRepo.findByStatusNot(com.shipmate.model.AppStatus.DRAFT)) {
            if (app.getStatus() == com.shipmate.model.AppStatus.PAUSED
                    || app.getStatus() == com.shipmate.model.AppStatus.FAILED) {
                continue;
            }
            try {
                List<GitHubClient.CommitInfo> commits = gitHub.listCommits(
                        app.getRepoOwner(), app.getRepoName(), app.getBranch(), 10);

                if (app.getPolledSha() == null) {
                    app.setPolledSha(commits.isEmpty() ? "" : commits.get(0).sha());
                    appRepo.save(app);
                    log.info("poller: app {} anchored at {} (initial sync, no deploys)", app.getName(), app.getPolledSha());
                    continue;
                }

                List<GitHubClient.CommitInfo> newCommits = new ArrayList<>();
                for (GitHubClient.CommitInfo c : commits) {
                    if (c.sha().equals(app.getPolledSha())) {
                        break;
                    }
                    if (isBotCommit(c.message())) {
                        continue;
                    }
                    newCommits.add(c);
                }
                Collections.reverse(newCommits);

                for (GitHubClient.CommitInfo c : newCommits) {
                    DeploymentRun run = new DeploymentRun();
                    run.setAppId(app.getId());
                    run.setAppName(app.getName());
                    run.setBranch(app.getBranch());
                    run.setTrigger(TriggerType.WEBHOOK);
                    run.setCommitSha(c.sha());
                    run.setImageTag(c.sha());
                    run.setCommitMessage(c.message());
                    runRepo.save(run);
                    log.info("poller: queued run for {} sha={} msg={}", app.getName(), c.sha(), c.message());
                }

                if (!commits.isEmpty()) {
                    app.setPolledSha(commits.get(0).sha());
                    appRepo.save(app);
                }
            } catch (Exception e) {
                log.warn("poller failed for {}: {}", app.getName(), e.getMessage());
            }
        }
    }

    private boolean isBotCommit(String message) {
        return message.contains("[skip ci]")
                || message.contains("[skip actions]")
                || message.startsWith("shipmate:")
                || message.startsWith("chore: shipmate")
                || message.startsWith("rollback:");
    }
}
