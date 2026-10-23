package com.testhub.uiexec.client;

/**
 * Raised when the agent cannot be reached or rejects a dispatch / cancel call.
 * Callers degrade to {@code EXECUTOR_UNAVAILABLE} / direct cancellation.
 */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }

    public AgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
