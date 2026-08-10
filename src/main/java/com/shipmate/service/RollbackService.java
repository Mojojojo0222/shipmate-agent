package com.shipmate.service;

import com.shipmate.git.GitHubClient;
import com.shipmate.model.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RollbackService {

    private static final Logger log = LoggerFactory.getLogger(RollbackService.class);

    private final GitHubClient gitHub;

    public RollbackService(GitHubClient gitHub) {
        this.gitHub = gitHub;
    }

    public String rollbackToPrevious(App app, String badSha) {
        try {
            var file = gitHub.getFile(app.getRepoOwner(), app.getRepoName(), app.getValuesPath(), app.getBranch());
            String content = file.content();

            String[] lines = content.split("\n");
            boolean foundBad = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("tag:")) {
                    String current = line.substring(line.indexOf(":") + 1).trim();
                    if (current.equals(badSha)) {
                        foundBad = true;
                    }
                }
            }
            if (!foundBad) {
                log.warn("values file tag {} not found for app {}", badSha, app.getName());
                return null;
            }

            String previous = gitHub.findPreviousCommitForFile(app.getRepoOwner(), app.getRepoName(), app.getValuesPath(), app.getBranch());
            if (previous == null) {
                return null;
            }
            if (previous.equals(badSha)) {
                return null;
            }

            String updated = content.replace("tag: " + badSha, "tag: " + previous);
            gitHub.putFile(app.getRepoOwner(), app.getRepoName(), app.getValuesPath(),
                    "rollback: shipmate reverts " + badSha + " -> " + previous + " [skip ci]",
                    updated, file.sha(), app.getBranch());
            return previous;
        } catch (Exception e) {
            log.error("rollback failed for app {}", app.getName(), e);
            return null;
        }
    }
}
