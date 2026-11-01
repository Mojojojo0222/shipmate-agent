package com.shipmate.controller;

import com.shipmate.model.DeploymentRun;
import com.shipmate.model.TriggerType;
import com.shipmate.repository.AlertRepository;
import com.shipmate.repository.AuditLogRepository;
import com.shipmate.repository.DeploymentRunRepository;
import com.shipmate.repository.HealthRecordRepository;
import com.shipmate.service.AppService;
import com.shipmate.service.AuditService;
import com.shipmate.service.DeployEngine;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AppService appService;
    private final DeploymentRunRepository runRepo;
    private final HealthRecordRepository healthRepo;
    private final AlertRepository alertRepo;
    private final AuditLogRepository auditRepo;
    private final AuditService audit;

    public ApiController(AppService appService, DeploymentRunRepository runRepo, HealthRecordRepository healthRepo,
                         AlertRepository alertRepo, AuditLogRepository auditRepo, AuditService audit) {
        this.appService = appService;
        this.runRepo = runRepo;
        this.healthRepo = healthRepo;
        this.alertRepo = alertRepo;
        this.auditRepo = auditRepo;
        this.audit = audit;
    }

    @PostMapping("/apps")
    public Object register(@RequestBody AppService.RegisterRequest req) {
        return appService.register(req);
    }

    @GetMapping("/apps")
    public Object apps() {
        return appService.list();
    }

    @GetMapping("/apps/{id}")
    public Object app(@PathVariable Long id) {
        return appService.get(id);
    }

    @PutMapping("/apps/{id}")
    public Object update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return appService.update(id, body);
    }

    @PostMapping("/apps/{id}/estimate")
    public Object estimate(@PathVariable Long id) {
        return appService.estimate(id);
    }

    @PostMapping("/apps/{id}/deploy")
    public Object deploy(@PathVariable Long id) {
        return appService.deploy(id);
    }

    @PostMapping("/apps/{id}/trigger")
    public Object trigger(@PathVariable Long id) {
        var app = appService.get(id);
        DeploymentRun run = new DeploymentRun();
        run.setAppId(app.getId());
        run.setAppName(app.getName());
        run.setBranch(app.getBranch());
        run.setTrigger(TriggerType.MANUAL);
        run.setCommitSha(app.getLastImageTag() == null ? "latest" : app.getLastImageTag());
        run.setImageTag(run.getCommitSha());
        run.setCommitMessage("manual redeploy");
        runRepo.save(run);
        audit.log("user", "app.trigger", "app=" + app.getName());
        return run;
    }

    @DeleteMapping("/apps/{id}")
    public Object remove(@PathVariable Long id) {
        appService.remove(id);
        return Map.of("removed", id);
    }

    @GetMapping("/runs")
    public Object runs() {
        return runRepo.findTop20ByOrderByIdDesc();
    }

    @GetMapping("/runs/{id}")
    public Object run(@PathVariable Long id) {
        return runRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("run not found: " + id));
    }

    @GetMapping("/apps/{id}/runs")
    public Object appRuns(@PathVariable Long id) {
        return runRepo.findByAppIdOrderByIdDesc(id);
    }

    @GetMapping("/health/latest")
    public Object healthLatest(@RequestParam(required = false) Long appId) {
        if (appId != null) {
            return healthRepo.findTop20ByAppIdOrderByIdDesc(appId);
        }
        return healthRepo.findTop100ByOrderByIdDesc();
    }

    @GetMapping("/alerts")
    public Object alerts() {
        return alertRepo.findTop50ByOrderByIdDesc();
    }

    @GetMapping("/audit")
    public Object audit() {
        return auditRepo.findTop100ByOrderByIdDesc();
    }
}
