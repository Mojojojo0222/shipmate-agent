package com.shipmate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmate.config.AgentProperties;
import com.shipmate.model.App;
import com.shipmate.model.DeploymentRun;
import com.shipmate.model.TriggerType;
import com.shipmate.repository.AppRepository;
import com.shipmate.repository.DeploymentRunRepository;
import com.shipmate.service.AuditService;
import com.shipmate.util.HmacVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final AppRepository appRepo;
    private final DeploymentRunRepository runRepo;
    private final AuditService audit;

    public WebhookController(AgentProperties props, ObjectMapper mapper, AppRepository appRepo,
                             DeploymentRunRepository runRepo, AuditService audit) {
        this.props = props;
        this.mapper = mapper;
        this.appRepo = appRepo;
        this.runRepo = runRepo;
        this.audit = audit;
    }

    @PostMapping("/github")
    public ResponseEntity<String> github(@RequestBody String body,
                                         @RequestHeader("X-GitHub-Event") String event,
                                         @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!HmacVerifier.verify(props.getGithub().getWebhookSecret(), body, signature)) {
            log.warn("webhook signature mismatch");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
        }
        if (!"push".equals(event)) {
            return ResponseEntity.ok("ignored: " + event);
        }
        try {
            JsonNode payload = mapper.readTree(body);
            String ref = payload.path("ref").asText();
            String branch = props.getGithub().getBranch();
            if (!ref.equals("refs/heads/" + branch)) {
                return ResponseEntity.ok("ignored: branch " + ref);
            }
            JsonNode head = payload.path("head_commit");
            if (head.isNull()) {
                return ResponseEntity.ok("ignored: no head commit");
            }
            String message = head.path("message").asText("");
            if (message.contains("[skip ci]") || message.contains("[skip actions]")) {
                return ResponseEntity.ok("ignored: skip ci");
            }
            if (message.startsWith("shipmate:") || message.startsWith("chore: shipmate")
                    || message.startsWith("rollback:")) {
                return ResponseEntity.ok("ignored: shipmate bot commit");
            }

            String owner = payload.path("repository").path("owner").path("login").asText();
            String repo = payload.path("repository").path("name").asText();

            App app = appRepo.findByRepoOwnerAndRepoName(owner, repo).orElse(null);
            if (app == null || app.getStatus() == com.shipmate.model.AppStatus.PAUSED) {
                return ResponseEntity.ok("ignored: repo not managed by shipmate");
            }

            DeploymentRun run = new DeploymentRun();
            run.setAppId(app.getId());
            run.setAppName(app.getName());
            run.setBranch(branch);
            run.setTrigger(TriggerType.WEBHOOK);
            run.setCommitSha(head.path("id").asText());
            run.setImageTag(head.path("id").asText());
            run.setCommitMessage(message);
            runRepo.save(run);
            audit.log("webhook", "push.detected", "app=" + app.getName() + " sha=" + run.getCommitSha() + " msg=" + message);
            return ResponseEntity.ok("accepted, run queued");
        } catch (Exception e) {
            log.error("webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
