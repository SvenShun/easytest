package com.testhub.core.feature.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An operation associated with a control (CLICK/SWIPE/INPUT/...). */
@Entity
@Table(name = "kb_action")
public class KbAction {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "control_id", nullable = false, length = 64)
    private String controlId;

    @Column(name = "action_type", length = 40, nullable = false)
    private String actionType;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "params", length = 2000)
    private String params;

    /** MANUAL | AI. */
    @Column(name = "source", length = 16)
    private String source = "MANUAL";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbAction() {
    }

    public KbAction(String id, String controlId, String actionType, String label, String params,
                    String source, boolean enabled, Instant createdAt) {
        this.id = id;
        this.controlId = controlId;
        this.actionType = actionType;
        this.label = label;
        this.params = params;
        this.source = source == null ? "MANUAL" : source;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getControlId() { return controlId; }
    public String getActionType() { return actionType; }
    public String getLabel() { return label; }
    public String getParams() { return params; }
    public String getSource() { return source; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setLabel(String label) { this.label = label; }
    public void setParams(String params) { this.params = params; }
    public void setSource(String source) { this.source = source; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
