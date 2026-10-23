package com.testhub.core.feature.support;

import com.testhub.core.config.ArtifactProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Default artifact store: files under {@code testhub.artifact.local-dir}. */
@Component
@ConditionalOnProperty(prefix = "testhub.artifact", name = "store", havingValue = "local", matchIfMissing = true)
public class LocalArtifactStore implements ArtifactStore {

    private final Path root;

    public LocalArtifactStore(ArtifactProperties properties) {
        this.root = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("写入产物失败: " + objectKey, e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("读取产物失败: " + objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new UncheckedIOException("删除产物失败: " + objectKey, e);
        }
    }

    @Override
    public String backend() {
        return "local";
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的 objectKey: " + objectKey);
        }
        return target;
    }
}
