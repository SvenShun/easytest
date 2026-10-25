package io.metersphere.uiexec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for the embedded UI execution module ({@code metersphere.uiexec.*}). */
@Component
@ConfigurationProperties(prefix = "metersphere.uiexec")
public class UiExecutionProperties {

    /** Device sub-server agent base URL (TestHub device-agent protocol). */
    private String agentBaseUrl = "http://127.0.0.1:8090";
    /** Shared agent token. */
    private String agentToken = "testhub-dev-agent-token";
    /** Base URL the agent uses to reach MeterSphere callbacks. */
    private String callbackBaseUrl = "http://127.0.0.1:8082";
    /** Device used when the request does not specify one. */
    private String defaultDeviceSerial = "simulator-0001";
    private String modelProvider = "mock";
    private String modelEndpoint = "";
    private String modelApiKey = "";
    private String modelId = "mock-vision";
    private int totalTimeoutSec = 120;
    private int stepTimeoutSec = 30;

    public String getAgentBaseUrl() {
        return agentBaseUrl;
    }

    public void setAgentBaseUrl(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl;
    }

    public String getAgentToken() {
        return agentToken;
    }

    public void setAgentToken(String agentToken) {
        this.agentToken = agentToken;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String getDefaultDeviceSerial() {
        return defaultDeviceSerial;
    }

    public void setDefaultDeviceSerial(String defaultDeviceSerial) {
        this.defaultDeviceSerial = defaultDeviceSerial;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getModelEndpoint() {
        return modelEndpoint;
    }

    public void setModelEndpoint(String modelEndpoint) {
        this.modelEndpoint = modelEndpoint;
    }

    public String getModelApiKey() {
        return modelApiKey;
    }

    public void setModelApiKey(String modelApiKey) {
        this.modelApiKey = modelApiKey;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public int getTotalTimeoutSec() {
        return totalTimeoutSec;
    }

    public void setTotalTimeoutSec(int totalTimeoutSec) {
        this.totalTimeoutSec = totalTimeoutSec;
    }

    public int getStepTimeoutSec() {
        return stepTimeoutSec;
    }

    public void setStepTimeoutSec(int stepTimeoutSec) {
        this.stepTimeoutSec = stepTimeoutSec;
    }
}
