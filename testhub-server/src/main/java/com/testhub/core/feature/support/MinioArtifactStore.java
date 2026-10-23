package com.testhub.core.feature.support;

import com.testhub.core.config.ArtifactProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** MinIO artifact store, enabled with {@code TESTHUB_ARTIFACT_STORE=minio}. */
@Component
@ConditionalOnProperty(prefix = "testhub.artifact", name = "store", havingValue = "minio")
public class MinioArtifactStore implements ArtifactStore {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioArtifactStore(ArtifactProperties properties) {
        ArtifactProperties.Minio minio = properties.getMinio();
        this.client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        this.bucket = minio.getBucket();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        ensureBucket();
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, content.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 写入失败: " + objectKey, e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        if (!exists(objectKey)) {
            return null;
        }
        try (java.io.InputStream input = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return input.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 读取失败: " + objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 删除失败: " + objectKey, e);
        }
    }

    @Override
    public String backend() {
        return "minio";
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                bucketReady = true;
            } catch (Exception e) {
                throw new IllegalStateException("MinIO bucket 初始化失败: " + bucket, e);
            }
        }
    }
}
