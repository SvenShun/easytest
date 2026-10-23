package com.testhub.core.feature.casemgmt;

/** Comma separated explicit selection in case create/update. */
public record CaseRequest(String title, String given, String when, String then, String requirementId,
                          String scriptId, String source) {
}
