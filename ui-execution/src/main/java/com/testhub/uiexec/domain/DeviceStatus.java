package com.testhub.uiexec.domain;

/** Device status is computed from heartbeat freshness, current task and queue. */
public enum DeviceStatus {
    IDLE,
    QUEUED,
    SCRIPT_RUNNING,
    AI_RUNNING,
    OFFLINE,
    MAINTENANCE
}
