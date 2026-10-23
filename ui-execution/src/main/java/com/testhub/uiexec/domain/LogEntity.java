package com.testhub.uiexec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Append-only task log; {@code seq} increases per task. */
@Entity
@Table(name = "ue_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_ue_log_task_seq", columnNames = {"task_id", "seq"}))
public class LogEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "level", length = 20)
    private String level;

    @Column(name = "message", length = 4000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LogEntity() {
    }

    public LogEntity(String id, String taskId, int seq, String level, String message, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.seq = seq;
        this.level = level;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getSeq() {
        return seq;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
