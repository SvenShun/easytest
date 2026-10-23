package com.testhub.uiexec.artifact;

import com.testhub.uiexec.config.UiExecProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO artifact store (opt-in via {@code testhub.uiexec.artifact-store=minio}).
 * Objects are keyed by the task-scoped relative path; the bucket is created on
 * first write.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "artifact-store", havingValue = "minio")
public class MinioArtifactStore implements ArtifactStore {

    private final UiExecProperties properties;
    private final MinioClient client;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioArtifactStore(UiExecProperties properties) {
        this.properties = properties;
        UiExecProperties.Minio minio = properties.getMinio();
        this.bucket = minio.getBucket() == null || minio.getBucket().isBlank() ? "testhub" : minio.getBucket();
        this.client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    @Override
    public String put(String taskId, String relativePath, byte[] content) {
        String relative = ArtifactPaths.combine(taskId, relativePath);
        try (InputStream in = new ByteArrayInputStream(content)) {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(relative)
                    .stream(in, content.length, -1)
                    .build());
            return relative;
        } catch (Exception e) {
            throw new IllegalStateException("写入 MinIO 产物失败: " + relative, e);
        }
    }

    @Override
    public String put(String taskId, String relativePath, InputStream content) {
        try {
            return put(taskId, relativePath, content.readAllBytes());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取产物内容失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String publicUrl(String relativePath) {
        String base = properties.getPublicBaseUrl();
        if (base != null && !base.isBlank()) {
            return properties.artifactUrl(relativePath);
        }
        String endpoint = properties.getMinio().getEndpoint();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + bucket + "/" + relativePath.replace('\\', '/');
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady.set(true);
    }
}
