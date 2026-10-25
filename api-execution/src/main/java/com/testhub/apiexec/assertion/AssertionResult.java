package com.testhub.apiexec.assertion;

/** Outcome of evaluating one assertion; serialized into the task report. */
public record AssertionResult(String type, String path, String operator, String expected,
                              String actual, boolean passed, String message) {

    public static AssertionResult pass(String type, String path, String operator, String expected,
                                       String actual) {
        return new AssertionResult(type, path, operator, expected, actual, true, null);
    }

    public static AssertionResult fail(String type, String path, String operator, String expected,
                                       String actual, String message) {
        return new AssertionResult(type, path, operator, expected, actual, false, message);
    }
}
