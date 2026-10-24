package com.testhub.uiexec.artifact;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Artifact port. Paths are task-scoped: {@code put("t1", "steps/1.png", ...)}
 * returns {@code "t1/steps/1.png"} and {@link #publicUrl(String)} turns it into
 * an absolute URL.
 */
public interface ArtifactStore {

    String put(String taskId, String relativePath, byte[] content);

    default String put(String taskId, String relativePath, InputStream content) {
        try {
            return put(taskId, relativePath, content.readAllBytes());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取产物内容失败: " + e.getMessage(), e);
        }
    }

    default String putBytes(String taskId, String relativePath, byte[] content) {
        return put(taskId, relativePath, new ByteArrayInputStream(content));
    }

    String publicUrl(String relativePath);

    /**
     * Reads an artifact by its task-scoped relative path, or {@code null} when
     * it does not exist. Backed by the same storage as {@link #put}, so S3 and
     * filesystem artifacts are served through one HTTP route.
     */
    byte[] read(String relativePath);
}
