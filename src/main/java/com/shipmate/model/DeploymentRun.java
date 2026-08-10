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
@Table(name = "deployment_runs")
public class DeploymentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appId;
    private String appName;
    private String commitSha;
    private String commitMessage;
    private String branch;

    @Enumerated(EnumType.STRING)
    private TriggerType trigger = TriggerType.MANUAL;

    @Enumerated(EnumType.STRING)
    private RunState state = RunState.PENDING;

    private String imageTag;
    private String workflowRunUrl;
    private String error;
    private int rollbackAttempts;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public TriggerType getTrigger() { return trigger; }
    public void setTrigger(TriggerType trigger) { this.trigger = trigger; }
    public RunState getState() { return state; }
    public void setState(RunState state) { this.state = state; }
    public String getImageTag() { return imageTag; }
    public void setImageTag(String imageTag) { this.imageTag = imageTag; }
    public String getWorkflowRunUrl() { return workflowRunUrl; }
    public void setWorkflowRunUrl(String workflowRunUrl) { this.workflowRunUrl = workflowRunUrl; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public int getRollbackAttempts() { return rollbackAttempts; }
    public void setRollbackAttempts(int rollbackAttempts) { this.rollbackAttempts = rollbackAttempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
