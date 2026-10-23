package com.testhub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "testhub.security")
public class SecurityProperties {

    /** HS256 secret shared by every TestHub service. */
    private String jwtSecret = "testhub-dev-jwt-secret-change-me-0123456789abcdef";
    private Duration tokenTtl = Duration.ofHours(12);

    /** Shared token used by internal (service-to-service) endpoints. */
    private String serviceToken = "testhub-dev-service-token";

    /** Shared token used by device agents for dispatch and callbacks. */
    private String agentToken = "testhub-dev-agent-token";

    /** AES key material used to encrypt model API keys at rest. */
    private String encryptionKey = "testhub-dev-encryption-key-change-me";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public String getAgentToken() {
        return agentToken;
    }

    public void setAgentToken(String agentToken) {
        this.agentToken = agentToken;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
