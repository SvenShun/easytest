package com.testhub.core.feature.casemgmt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One declarative, deterministic step of a {@link ClientCase}. Ordered by {@code seq} within a case.
 *
 * <p>The owning FK is {@code case_id}; {@link #getCaseId()} exposes the raw id for projections and
 * the plan task payload without serialising the parent association.</p>
 */
@Entity
@Table(name = "case_step", indexes = @Index(name = "idx_case_step_case", columnList = "case_id"))
public class CaseStep {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private ClientCase clientCase;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 32)
    private StepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(name = "selector_type", length = 16)
    private SelectorType selectorType;

    @Column(name = "selector", length = 1000)
    private String selector;

    @Column(name = "step_value", length = 4000)
    private String value;

    @Column(name = "assert_value", length = 4000)
    private String assertValue;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected CaseStep() {
    }

    public CaseStep(String id) {
        this.id = id;
    }

    public CaseStep(String id, ClientCase clientCase, int seq, StepType stepType, SelectorType selectorType,
                    String selector, String value, String assertValue, int timeoutMs, boolean enabled) {
        this.id = id;
        this.clientCase = clientCase;
        this.seq = seq;
        this.stepType = stepType;
        this.selectorType = selectorType;
        this.selector = selector;
        this.value = value;
        this.assertValue = assertValue;
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getCaseId() {
        return clientCase == null ? null : clientCase.getId();
    }

    public ClientCase getClientCase() {
        return clientCase;
    }

    public void setClientCase(ClientCase clientCase) {
        this.clientCase = clientCase;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public StepType getStepType() {
        return stepType;
    }

    public void setStepType(StepType stepType) {
        this.stepType = stepType;
    }

    public SelectorType getSelectorType() {
        return selectorType;
    }

    public void setSelectorType(SelectorType selectorType) {
        this.selectorType = selectorType;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getAssertValue() {
        return assertValue;
    }

    public void setAssertValue(String assertValue) {
        this.assertValue = assertValue;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
