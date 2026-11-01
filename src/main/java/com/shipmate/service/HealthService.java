package com.shipmate.service;

import com.shipmate.k8s.K8sService;
import com.shipmate.model.App;
import com.shipmate.model.HealthRecord;
import com.shipmate.repository.HealthRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final String DEFAULT_HEALTH_PATH = "/actuator/health";

    private final HealthRecordRepository healthRepo;
    private final K8sService k8s;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HealthService(HealthRecordRepository healthRepo, K8sService k8s) {
        this.healthRepo = healthRepo;
        this.k8s = k8s;
    }

    public static class HealthResult {
        public final boolean up;
        public final int httpStatus;
        public final long latencyMs;

        public HealthResult(boolean up, int httpStatus, long latencyMs) {
            this.up = up;
            this.httpStatus = httpStatus;
            this.latencyMs = latencyMs;
        }

        public boolean isUp() {
            return up;
        }
    }

    public HealthResult checkNow(App app) {
        long start = System.currentTimeMillis();
        String path = DEFAULT_HEALTH_PATH;
        try {
            URI uri = URI.create(app.getHealthUrl());
            if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
                path = uri.getPath();
            }
        } catch (Exception ignore) {
        }
        try {
            String podName = k8s.findReadyPod(app.getNamespace(), app.getName());
            if (podName == null) {
                record(app, "DOWN", 0, System.currentTimeMillis() - start);
                return new HealthResult(false, 0, System.currentTimeMillis() - start);
            }
            int containerPort = k8s.getContainerPort(app.getNamespace(), podName);
            try (com.shipmate.k8s.K8sService.PortForwardInfo pf = k8s.portForwardPod(app.getNamespace(), podName, containerPort)) {
                if (pf == null) {
                    record(app, "DOWN", 0, System.currentTimeMillis() - start);
                    return new HealthResult(false, 0, System.currentTimeMillis() - start);
                }
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + pf.localPort() + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                long latency = System.currentTimeMillis() - start;
                boolean up = resp.statusCode() >= 200 && resp.statusCode() < 500;
                record(app, up ? "UP" : "DOWN", resp.statusCode(), latency);
                return new HealthResult(up, resp.statusCode(), latency);
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            long latency = System.currentTimeMillis() - start;
            record(app, "DOWN", 0, latency);
            return new HealthResult(false, 0, latency);
        }
    }

    private void record(App app, String status, int httpStatus, long latencyMs) {
        HealthRecord rec = new HealthRecord();
        rec.setAppId(app.getId());
        rec.setAppName(app.getName());
        rec.setStatus(status);
        rec.setHttpStatus(httpStatus);
        rec.setLatencyMs(latencyMs);
        healthRepo.save(rec);
    }
}