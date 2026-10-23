package com.testhub.core.feature.support;

import java.io.IOException;
import java.io.InputStream;

/** Port for binary artifacts (script bundles, reports). Default is local disk. */
public interface ArtifactStore {

    /** Stores (or overwrites) the object. */
    void put(String objectKey, byte[] content);

    /** Reads the object, or {@code null} when it does not exist. */
    byte[] get(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);

    /** Human readable backend name ("local" / "minio"). */
    String backend();

    default void put(String objectKey, InputStream inputStream) {
        try {
            put(objectKey, inputStream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传内容失败", e);
        }
    }
}
