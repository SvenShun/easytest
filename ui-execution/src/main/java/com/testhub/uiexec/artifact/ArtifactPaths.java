package com.testhub.uiexec.artifact;

/** Shared path normalisation for artifact stores. */
public final class ArtifactPaths {

    private ArtifactPaths() {
    }

    /** Joins taskId and relativePath with forward slashes, rejecting traversal. */
    public static String combine(String taskId, String relativePath) {
        String task = normalizeSegment(taskId);
        String relative = relativePath == null ? "" : relativePath.replace('\\', '/').trim();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.contains("..")) {
            throw new IllegalArgumentException("非法的产物路径: " + relativePath);
        }
        return task.isEmpty() ? relative : task + "/" + relative;
    }

    /** Sanitizes an artifact file name coming from an agent callback. */
    public static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "artifact.bin";
        }
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..") ? "artifact.bin" : cleaned;
    }

    private static String normalizeSegment(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.contains("..")) {
            throw new IllegalArgumentException("非法的任务 ID: " + value);
        }
        return cleaned;
    }
}
