package com.testhub.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Runtime settings owned by testhub-server (public URLs and ticket lifetime). */
@ConfigurationProperties(prefix = "testhub.core")
public class CoreProperties {

    /** Base URL that agents/runtimes use to reach this server (ticket links). */
    private String publicBaseUrl = "http://127.0.0.1:8080";

    /**
     * Base URL the device-agent uses to download script bundles. Defaults to
     * {@link #publicBaseUrl}; container deployments set an internal address.
     */
    private String internalBaseUrl;

    /** Base URL of the ui-execution service. */
    private String uiExecutionBaseUrl = "http://127.0.0.1:8081";

    /** Lifetime of a script bundle download ticket. */
    private Duration ticketTtl = Duration.ofMinutes(15);

    /** Lifetime of a model credential handed to ui-execution / agents. */
    private Duration credentialTtl = Duration.ofMinutes(15);

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getInternalBaseUrl() {
        return internalBaseUrl == null || internalBaseUrl.isBlank() ? publicBaseUrl : internalBaseUrl;
    }

    public void setInternalBaseUrl(String internalBaseUrl) {
        this.internalBaseUrl = internalBaseUrl;
    }

    public String getUiExecutionBaseUrl() {
        return uiExecutionBaseUrl;
    }

    public void setUiExecutionBaseUrl(String uiExecutionBaseUrl) {
        this.uiExecutionBaseUrl = uiExecutionBaseUrl;
    }

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = ticketTtl;
    }

    public Duration getCredentialTtl() {
        return credentialTtl;
    }

    public void setCredentialTtl(Duration credentialTtl) {
        this.credentialTtl = credentialTtl;
    }
}
