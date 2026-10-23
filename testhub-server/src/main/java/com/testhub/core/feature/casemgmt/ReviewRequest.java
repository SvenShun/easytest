package com.testhub.core.feature.casemgmt;

/** Review action payload. Comment is optional. */
public record ReviewRequest(String action, String comment) {
}
