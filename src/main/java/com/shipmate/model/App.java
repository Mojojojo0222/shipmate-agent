package com.shipmate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "apps")
public class App {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String repoUrl;
    private String repoOwner;
    private String repoName;
    private String branch;
    private String workflowFile;
    private String valuesPath;
    private String namespace;
    private String healthUrl;
    private int containerPort = 8080;

    private int trafficRpm;
    private int trafficUsers;

    @Column(length = 4000)
    private String specYaml;

    @Column(length = 4000)
    private String costBreakdownJson;

    private double estimatedMonthlyCost;
    private String deployedUrl;
    private String lastImageTag;
    private String polledSha;

    @Enumerated(EnumType.STRING)
    private AppStatus status = AppStatus.DRAFT;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getRepoOwner() { return repoOwner; }
    public void setRepoOwner(String repoOwner) { this.repoOwner = repoOwner; }
    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getWorkflowFile() { return workflowFile; }
    public void setWorkflowFile(String workflowFile) { this.workflowFile = workflowFile; }
    public String getValuesPath() { return valuesPath; }
    public void setValuesPath(String valuesPath) { this.valuesPath = valuesPath; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getHealthUrl() { return healthUrl; }
    public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }
    public int getContainerPort() { return containerPort; }
    public void setContainerPort(int containerPort) { this.containerPort = containerPort; }
    public int getTrafficRpm() { return trafficRpm; }
    public void setTrafficRpm(int trafficRpm) { this.trafficRpm = trafficRpm; }
    public int getTrafficUsers() { return trafficUsers; }
    public void setTrafficUsers(int trafficUsers) { this.trafficUsers = trafficUsers; }
    public String getSpecYaml() { return specYaml; }
    public void setSpecYaml(String specYaml) { this.specYaml = specYaml; }
    public String getCostBreakdownJson() { return costBreakdownJson; }
    public void setCostBreakdownJson(String costBreakdownJson) { this.costBreakdownJson = costBreakdownJson; }
    public double getEstimatedMonthlyCost() { return estimatedMonthlyCost; }
    public void setEstimatedMonthlyCost(double estimatedMonthlyCost) { this.estimatedMonthlyCost = estimatedMonthlyCost; }
    public String getDeployedUrl() { return deployedUrl; }
    public void setDeployedUrl(String deployedUrl) { this.deployedUrl = deployedUrl; }
    public String getLastImageTag() { return lastImageTag; }
    public void setLastImageTag(String lastImageTag) { this.lastImageTag = lastImageTag; }
    public String getPolledSha() { return polledSha; }
    public void setPolledSha(String polledSha) { this.polledSha = polledSha; }
    public AppStatus getStatus() { return status; }
    public void setStatus(AppStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
