package com.testhub.core.feature.casemgmt;

/** BDD triple used by detail, plan and execution-context payloads. */
public record BddView(String given, String when, String then) {
}
