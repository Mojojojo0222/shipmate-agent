package com.shipmate.k8s;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.PortForward;
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
        if (svc == null) return null;
        if ("NodePort".equals(svc.getSpec().getType())) {
            if (svc.getSpec().getPorts() != null && !svc.getSpec().getPorts().isEmpty()
                    && svc.getSpec().getPorts().get(0).getNodePort() != null) {
                return "http://localhost:" + svc.getSpec().getPorts().get(0).getNodePort();
            }
            return null;
        }
        if (svc.getStatus() == null || svc.getStatus().getLoadBalancer() == null
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

    public boolean applyYaml(String yaml) {
        try {
            client.load(new java.io.ByteArrayInputStream(yaml.getBytes()))
                    .createOrReplace();
            return true;
        } catch (Exception e) {
            log.warn("applyYaml failed: {}", e.getMessage());
            return false;
        }
    }

    public Optional<String> findDeploymentForNamespace(String namespace) {
        var deployments = client.apps().deployments().inNamespace(namespace).list().getItems();
        if (deployments.isEmpty()) return Optional.empty();
        return Optional.of(deployments.get(0).getMetadata().getName());
    }

    public String findReadyPod(String namespace, String deploymentName) {
        var pods = client.pods().inNamespace(namespace).list().getItems();
        return pods.stream()
                .filter(p -> p.getMetadata().getName().startsWith(deploymentName + "-"))
                .filter(p -> p.getStatus() != null && p.getStatus().getContainerStatuses() != null
                        && p.getStatus().getContainerStatuses().stream()
                                .anyMatch(cs -> Boolean.TRUE.equals(cs.getReady())))
                .map(p -> p.getMetadata().getName())
                .findFirst()
                .orElse(null);
    }

    public int getContainerPort(String namespace, String podName) {
        var pod = client.pods().inNamespace(namespace).withName(podName).get();
        if (pod == null || pod.getSpec() == null || pod.getSpec().getContainers() == null
                || pod.getSpec().getContainers().isEmpty()) {
            return 8080;
        }
        var ports = pod.getSpec().getContainers().get(0).getPorts();
        if (ports != null && !ports.isEmpty() && ports.get(0).getContainerPort() != null) {
            return ports.get(0).getContainerPort();
        }
        return 8080;
    }

    public PortForwardInfo portForwardPod(String namespace, String podName, int containerPort) {
        int localPort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            localPort = probe.getLocalPort();
        } catch (Exception e) {
            log.warn("no free local port: {}", e.getMessage());
            return null;
        }
        try {
            PortForward pf = client.pods().inNamespace(namespace).withName(podName).portForward(containerPort, localPort);
            return pf == null ? null : new PortForwardInfo(pf, localPort);
        } catch (Exception e) {
            log.warn("port forward failed for {}: {}", podName, e.getMessage());
            return null;
        }
    }

    public record PortForwardInfo(PortForward pf, int localPort) implements AutoCloseable {
        @Override
        public void close() {
            if (pf != null) {
                try {
                    pf.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
