package com.testhub.uiexec.client;

/** Raised when a testhub-server internal call fails or is unreachable. */
public class CoreClientException extends RuntimeException {

    private final int statusCode;

    public CoreClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public CoreClientException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
