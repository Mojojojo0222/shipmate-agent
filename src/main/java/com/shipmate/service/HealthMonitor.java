package com.shipmate.service;

import com.shipmate.config.AgentProperties;
import com.shipmate.k8s.K8sService;
import com.shipmate.model.App;
import com.shipmate.model.Alert;
import com.shipmate.model.HealthRecord;
import com.shipmate.repository.AlertRepository;
import com.shipmate.repository.AppRepository;
import com.shipmate.repository.HealthRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitor.class);

    private final AppRepository appRepo;
    private final HealthRecordRepository healthRepo;
    private final AlertRepository alertRepo;
    private final HealthService healthService;
    private final K8sService k8s;
    private final CostEstimator costEstimator;
    private final AgentProperties props;

    private final Map<Long, Integer> consecutiveFailures = new HashMap<>();
    private final Map<Long, Integer> lastReplicas = new HashMap<>();

    public HealthMonitor(AppRepository appRepo, HealthRecordRepository healthRepo, AlertRepository alertRepo,
                         HealthService healthService, K8sService k8s, CostEstimator costEstimator,
                         AgentProperties props) {
        this.appRepo = appRepo;
        this.healthRepo = healthRepo;
        this.alertRepo = alertRepo;
        this.healthService = healthService;
        this.k8s = k8s;
        this.costEstimator = costEstimator;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${shipmate.monitor.interval-sec}000")
    public void monitor() {
        List<App> apps = appRepo.findByStatusNot(com.shipmate.model.AppStatus.DRAFT);
        for (App app : apps) {
            if (app.getStatus() == com.shipmate.model.AppStatus.DEPLOYING
                    || app.getStatus() == com.shipmate.model.AppStatus.FAILED
                    || app.getStatus() == com.shipmate.model.AppStatus.PAUSED
                    || app.getStatus() == com.shipmate.model.AppStatus.DRAFT) {
                continue;
            }
            checkHealth(app);
            checkReplicas(app);
        }
    }

    private void checkHealth(App app) {
        var result = healthService.checkNow(app);
        if (!result.up) {
            int failures = consecutiveFailures.merge(app.getId(), 1, Integer::sum);
            app.setStatus(com.shipmate.model.AppStatus.DEGRADED);
            appRepo.save(app);

            if (failures >= props.getMonitor().getRestartAfterFailures()) {
                boolean restarted = k8s.restartDeployment(app.getNamespace(), app.getName());
                alert(app, "SELF_HEAL",
                        "app unhealthy for " + failures + " checks — auto-restarted deployment " + app.getName()
                                + (restarted ? "" : " (restart failed)"));
                consecutiveFailures.put(app.getId(), 0);
            }
        } else {
            consecutiveFailures.put(app.getId(), 0);
            if (app.getStatus() == com.shipmate.model.AppStatus.DEGRADED) {
                app.setStatus(com.shipmate.model.AppStatus.RUNNING);
                appRepo.save(app);
                alert(app, "RECOVERED", "app " + app.getName() + " is healthy again");
            }
        }
    }

    private void checkReplicas(App app) {
        try {
            int replicas = k8s.getReadyReplicas(app.getNamespace(), app.getName());
            Integer previous = lastReplicas.get(app.getId());
            if (previous != null && previous != replicas) {
                double delta = costEstimator.deltaForReplicas(previous, replicas);
                String direction = replicas > previous ? "scaled UP" : "scaled DOWN";
                alert(app, "SCALE",
                        app.getName() + " " + direction + " " + previous + " -> " + replicas + " pods (~$"
                                + Math.abs(delta) + "/mo change)");
            }
            lastReplicas.put(app.getId(), replicas);
        } catch (Exception e) {
            log.debug("replica check skipped for {}: {}", app.getName(), e.getMessage());
        }
    }

    private void alert(App app, String type, String message) {
        Alert a = new Alert();
        a.setAppId(app.getId());
        a.setAppName(app.getName());
        a.setType(type);
        a.setMessage(message);
        alertRepo.save(a);
        log.warn("[{}] {}", type, message);
    }
}
