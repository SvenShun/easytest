package com.testhub.uiexec.artifact;

import com.testhub.uiexec.config.UiExecProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Default filesystem artifact store, served under {@code /artifacts/**}. */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "artifact-store", havingValue = "local", matchIfMissing = true)
public class LocalArtifactStore implements ArtifactStore {

    private final Path root;
    private final UiExecProperties properties;

    public LocalArtifactStore(UiExecProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.getArtifactDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建产物目录: " + root, e);
        }
    }

    @Override
    public String put(String taskId, String relativePath, byte[] content) {
        String path = store(taskId, relativePath, content);
        return path;
    }

    @Override
    public String put(String taskId, String relativePath, InputStream content) {
        try {
            return store(taskId, relativePath, content.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("读取产物内容失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String publicUrl(String relativePath) {
        return properties.artifactUrl(relativePath);
    }

    private String store(String taskId, String relativePath, byte[] content) {
        String relative = ArtifactPaths.combine(taskId, relativePath);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的产物路径: " + relativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return relative;
        } catch (IOException e) {
            throw new IllegalStateException("写入产物失败: " + relative, e);
        }
    }
}
