package com.shipmate.k8s;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class K8sService {

    private static final Logger log = LoggerFactory.getLogger(K8sService.class);
    private final KubernetesClient client;

    public K8sService() {
        this.client = new KubernetesClientBuilder().build();
    }

    public String getDeploymentImage(String namespace, String name) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) return null;
        return d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
    }

    public boolean isRolloutComplete(String namespace, String name) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null) return false;
        long desired = d.getSpec().getReplicas() != null ? d.getSpec().getReplicas() : 1;
        boolean generation = d.getStatus() != null
                && d.getStatus().getObservedGeneration() != null
                && d.getStatus().getObservedGeneration() >= d.getMetadata().getGeneration();
        boolean replicas = d.getStatus() != null
                && d.getStatus().getUpdatedReplicas() != null
                && d.getStatus().getUpdatedReplicas() >= desired
                && d.getStatus().getReplicas() != null
                && d.getStatus().getReplicas() == desired;
        boolean available = false;
        if (d.getStatus() != null && d.getStatus().getConditions() != null) {
            for (DeploymentCondition c : d.getStatus().getConditions()) {
                if ("Available".equals(c.getType()) && "True".equals(c.getStatus())) {
                    available = true;
                }
            }
        }
        return generation && replicas && available;
    }

    public boolean waitForDeploymentImage(String namespace, String name, String expectedImageTag, int timeoutMinutes) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMinutes * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            String image = getDeploymentImage(namespace, name);
            if (image != null && image.endsWith(expectedImageTag) && isRolloutComplete(namespace, name)) {
                return true;
            }
            Thread.sleep(10_000);
        }
        return false;
    }

    public boolean restartDeployment(String namespace, String name) {
        try {
            client.apps().deployments().inNamespace(namespace).withName(name).rolling().restart();
            return true;
        } catch (Exception e) {
            log.warn("restart failed {}/{}: {}", namespace, name, e.getMessage());
            return false;
        }
    }

    public String getServiceEndpoint(String namespace, String name) {
        var svc = client.services().inNamespace(namespace).withName(name).get();
        if (svc == null || svc.getStatus() == null || svc.getStatus().getLoadBalancer() == null
                || svc.getStatus().getLoadBalancer().getIngress() == null
                || svc.getStatus().getLoadBalancer().getIngress().isEmpty()) {
            return null;
        }
        var ingress = svc.getStatus().getLoadBalancer().getIngress().get(0);
        if (ingress.getHostname() != null && !ingress.getHostname().isEmpty()) {
            return "http://" + ingress.getHostname();
        }
        if (ingress.getIp() != null && !ingress.getIp().isEmpty()) {
            return "http://" + ingress.getIp();
        }
        return null;
    }

    public int getReadyReplicas(String namespace, String name) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (d == null || d.getStatus() == null || d.getStatus().getReadyReplicas() == null) return 0;
        return d.getStatus().getReadyReplicas();
    }

    public Optional<String> findDeploymentForNamespace(String namespace) {
        var deployments = client.apps().deployments().inNamespace(namespace).list().getItems();
        if (deployments.isEmpty()) return Optional.empty();
        return Optional.of(deployments.get(0).getMetadata().getName());
    }
}
