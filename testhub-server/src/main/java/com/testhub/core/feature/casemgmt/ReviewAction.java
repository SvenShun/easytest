package com.testhub.core.feature.casemgmt;

/** Review actions accepted by {@code POST /api/v1/cases/{id}/review}. */
public enum ReviewAction {
    SUBMIT,
    APPROVE,
    REJECT
}
