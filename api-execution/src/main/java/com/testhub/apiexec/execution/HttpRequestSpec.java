package com.testhub.apiexec.execution;

/** Fully prepared request handed to the JDK HttpClient. */
public record HttpRequestSpec(String method, String url, java.util.Map<String, String> headers,
                              String body, int timeoutSec) {
}
