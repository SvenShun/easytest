package com.testhub.agent.skills;

/** Raised when Skills cannot be fetched and no cached commit exists. */
public class SkillsUnavailableException extends RuntimeException {

    public SkillsUnavailableException(String message) {
        super(message);
    }

    public SkillsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
