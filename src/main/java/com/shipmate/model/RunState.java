package com.shipmate.model;

public enum RunState {
    PENDING,
    TRIGGERING,
    CI_RUNNING,
    CI_FAILED,
    DEPLOYING,
    VERIFYING,
    HEALTHY,
    UNHEALTHY,
    ROLLING_BACK,
    ROLLED_BACK,
    FAILED,
    SKIPPED
}
