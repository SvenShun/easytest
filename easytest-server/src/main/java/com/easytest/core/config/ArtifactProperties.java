package com.easytest.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Artifact storage settings. {@code easytest.artifact.store=local|minio}; the
 * environment variable {@code EASYTEST_ARTIFACT_STORE} maps to the same key.
 */
@ConfigurationProperties(prefix = "easytest.artifact")
public class ArtifactProperties {

    private String store = "local";
    private String localDir = "./data/core-artifacts";
    private final Minio minio = new Minio();

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public Minio getMinio() {
        return minio;
    }

    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "easytest";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
