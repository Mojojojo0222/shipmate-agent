package com.shipmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "shipmate")
public class AgentProperties {

    private Github github = new Github();
    private Gitops gitops = new Gitops();
    private K8s k8s = new K8s();
    private Pipeline pipeline = new Pipeline();
    private Monitor monitor = new Monitor();
    private Aws aws = new Aws();
    private Pricing pricing = new Pricing();
    private Registry registry = new Registry();

    public Github getGithub() { return github; }
    public void setGithub(Github github) { this.github = github; }
    public Gitops getGitops() { return gitops; }
    public void setGitops(Gitops gitops) { this.gitops = gitops; }
    public K8s getK8s() { return k8s; }
    public void setK8s(K8s k8s) { this.k8s = k8s; }
    public Pipeline getPipeline() { return pipeline; }
    public void setPipeline(Pipeline pipeline) { this.pipeline = pipeline; }
    public Monitor getMonitor() { return monitor; }
    public void setMonitor(Monitor monitor) { this.monitor = monitor; }
    public Aws getAws() { return aws; }
    public void setAws(Aws aws) { this.aws = aws; }
    public Pricing getPricing() { return pricing; }
    public void setPricing(Pricing pricing) { this.pricing = pricing; }
    public Registry getRegistry() { return registry; }
    public void setRegistry(Registry registry) { this.registry = registry; }

    public static class Registry {
        private String url = "ghcr.io";
        private String owner = "Mojojojo0222";
        private String type = "ghcr";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class Github {
        private String apiUrl = "https://api.github.com";
        private String owner = "Mojojojo0222";
        private String token = "";
        private String webhookSecret = "changeme";
        private String ciWorkflow = "shipmate-deploy.yml";
        private String branch = "main";
        private String appId = "";
        private String installationId = "";
        private String privateKey = "";
        private boolean pollEnabled = false;
        private int pollIntervalSec = 30;

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
        public String getCiWorkflow() { return ciWorkflow; }
        public void setCiWorkflow(String ciWorkflow) { this.ciWorkflow = ciWorkflow; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getInstallationId() { return installationId; }
        public void setInstallationId(String installationId) { this.installationId = installationId; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public boolean isPollEnabled() { return pollEnabled; }
        public void setPollEnabled(boolean pollEnabled) { this.pollEnabled = pollEnabled; }
        public int getPollIntervalSec() { return pollIntervalSec; }
        public void setPollIntervalSec(int pollIntervalSec) { this.pollIntervalSec = pollIntervalSec; }
    }

    public static class Gitops {
        private String manifestsPath = "shipmate";
        private String branch = "main";

        public String getManifestsPath() { return manifestsPath; }
        public void setManifestsPath(String manifestsPath) { this.manifestsPath = manifestsPath; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
    }

    public static class K8s {
        private String namespacePrefix = "shipmate";
        private String serviceType = "LoadBalancer";

        public String getNamespacePrefix() { return namespacePrefix; }
        public void setNamespacePrefix(String namespacePrefix) { this.namespacePrefix = namespacePrefix; }
        public String getServiceType() { return serviceType; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    }

    public static class Pipeline {
        private int ciTimeoutMinutes = 15;
        private int deployTimeoutMinutes = 12;
        private int healthRetries = 6;
        private int healthIntervalSeconds = 10;
        private boolean rollbackEnabled = true;
        private int maxRollbacks = 1;

        public int getCiTimeoutMinutes() { return ciTimeoutMinutes; }
        public void setCiTimeoutMinutes(int ciTimeoutMinutes) { this.ciTimeoutMinutes = ciTimeoutMinutes; }
        public int getDeployTimeoutMinutes() { return deployTimeoutMinutes; }
        public void setDeployTimeoutMinutes(int deployTimeoutMinutes) { this.deployTimeoutMinutes = deployTimeoutMinutes; }
        public int getHealthRetries() { return healthRetries; }
        public void setHealthRetries(int healthRetries) { this.healthRetries = healthRetries; }
        public int getHealthIntervalSeconds() { return healthIntervalSeconds; }
        public void setHealthIntervalSeconds(int healthIntervalSeconds) { this.healthIntervalSeconds = healthIntervalSeconds; }
        public boolean isRollbackEnabled() { return rollbackEnabled; }
        public void setRollbackEnabled(boolean rollbackEnabled) { this.rollbackEnabled = rollbackEnabled; }
        public int getMaxRollbacks() { return maxRollbacks; }
        public void setMaxRollbacks(int maxRollbacks) { this.maxRollbacks = maxRollbacks; }
    }

    public static class Monitor {
        private int intervalSeconds = 60;
        private int restartAfterFailures = 3;

        public int getIntervalSeconds() { return intervalSeconds; }
        public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
        public int getRestartAfterFailures() { return restartAfterFailures; }
        public void setRestartAfterFailures(int restartAfterFailures) { this.restartAfterFailures = restartAfterFailures; }
    }

    public static class Aws {
        private String region = "ap-south-1";
        private String ecrRepositoryPrefix = "shipmate";
        private String githubActionsRoleArn = "";

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getEcrRepositoryPrefix() { return ecrRepositoryPrefix; }
        public void setEcrRepositoryPrefix(String ecrRepositoryPrefix) { this.ecrRepositoryPrefix = ecrRepositoryPrefix; }
        public String getGithubActionsRoleArn() { return githubActionsRoleArn; }
        public void setGithubActionsRoleArn(String githubActionsRoleArn) { this.githubActionsRoleArn = githubActionsRoleArn; }
    }

    public static class Pricing {
        private String region = "ap-south-1";
        private List<InstancePrice> instances = new ArrayList<>();
        private List<DbPrice> databases = new ArrayList<>();
        private double storagePerGiB = 0.08;
        private double buffer = 1.2;
        private Overhead overhead = new Overhead();

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public List<InstancePrice> getInstances() { return instances; }
        public void setInstances(List<InstancePrice> instances) { this.instances = instances; }
        public List<DbPrice> getDatabases() { return databases; }
        public void setDatabases(List<DbPrice> databases) { this.databases = databases; }
        public double getStoragePerGiB() { return storagePerGiB; }
        public void setStoragePerGiB(double storagePerGiB) { this.storagePerGiB = storagePerGiB; }
        public double getBuffer() { return buffer; }
        public void setBuffer(double buffer) { this.buffer = buffer; }
        public Overhead getOverhead() { return overhead; }
        public void setOverhead(Overhead overhead) { this.overhead = overhead; }

        public static class InstancePrice {
            private String name;
            private int vcpu;
            private int memoryGiB;
            private double priceMonthly;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public int getVcpu() { return vcpu; }
            public void setVcpu(int vcpu) { this.vcpu = vcpu; }
            public int getMemoryGiB() { return memoryGiB; }
            public void setMemoryGiB(int memoryGiB) { this.memoryGiB = memoryGiB; }
            public double getPriceMonthly() { return priceMonthly; }
            public void setPriceMonthly(double priceMonthly) { this.priceMonthly = priceMonthly; }
        }

        public static class DbPrice {
            private String name;
            private int maxWriteRpm;
            private int storageGiB;
            private double priceMonthly;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public int getMaxWriteRpm() { return maxWriteRpm; }
            public void setMaxWriteRpm(int maxWriteRpm) { this.maxWriteRpm = maxWriteRpm; }
            public int getStorageGiB() { return storageGiB; }
            public void setStorageGiB(int storageGiB) { this.storageGiB = storageGiB; }
            public double getPriceMonthly() { return priceMonthly; }
            public void setPriceMonthly(double priceMonthly) { this.priceMonthly = priceMonthly; }
        }

        public static class Overhead {
            private double controlPlane = 72.0;
            private double natGateway = 32.0;
            private double loadBalancer = 18.0;
            private double registry = 1.5;

            public double getControlPlane() { return controlPlane; }
            public void setControlPlane(double controlPlane) { this.controlPlane = controlPlane; }
            public double getNatGateway() { return natGateway; }
            public void setNatGateway(double natGateway) { this.natGateway = natGateway; }
            public double getLoadBalancer() { return loadBalancer; }
            public void setLoadBalancer(double loadBalancer) { this.loadBalancer = loadBalancer; }
            public double getRegistry() { return registry; }
            public void setRegistry(double registry) { this.registry = registry; }
        }
    }
}
