package com.testhub.uiexec.client;

import com.testhub.common.security.AuthFilter;
import com.testhub.common.security.SecurityProperties;
import com.testhub.uiexec.client.dto.DispatchPayload;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Client for the device agent ({@code POST /agent/tasks}, {@code DELETE
 * /agent/tasks/{taskId}}), authenticated with {@code X-Agent-Token}.
 */
@Component
public class AgentClient {

    private final RestClient.Builder builder;
    private final SecurityProperties security;

    public AgentClient(RestClient.Builder builder, SecurityProperties security) {
        this.builder = builder;
        this.security = security;
    }

    public void dispatch(String agentBaseUrl, DispatchPayload payload) {
        if (agentBaseUrl == null || agentBaseUrl.isBlank()) {
            throw new AgentUnavailableException("Agent 未提供 baseUrl");
        }
        try {
            client(agentBaseUrl).post()
                    .uri("/agent/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AgentUnavailableException("下发任务失败: " + e.getMessage(), e);
        }
    }

    public void cancel(String agentBaseUrl, String taskId) {
        if (agentBaseUrl == null || agentBaseUrl.isBlank()) {
            throw new AgentUnavailableException("Agent 未提供 baseUrl");
        }
        try {
            client(agentBaseUrl).delete()
                    .uri("/agent/tasks/{taskId}", taskId)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new AgentUnavailableException("取消任务失败: " + e.getMessage(), e);
        }
    }

    public byte[] screenshot(String agentBaseUrl, String serial) {
        try {
            return client(agentBaseUrl).get()
                    .uri("/device/{serial}/screenshot", serial)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("获取设备截图失败: " + e.getMessage(), e);
        }
    }

    public Object uiTree(String agentBaseUrl, String serial) {
        try {
            return client(agentBaseUrl).get()
                    .uri("/device/{serial}/ui-tree", serial)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("获取设备 UI 树失败: " + e.getMessage(), e);
        }
    }

    public Object shell(String agentBaseUrl, String serial, String command, int timeoutSec) {
        try {
            return client(agentBaseUrl).post()
                    .uri("/device/{serial}/shell", serial)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .body(Map.of("command", command, "timeoutSec", timeoutSec))
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("执行 shell 失败: " + e.getMessage(), e);
        }
    }

    public Object apps(String agentBaseUrl, String serial) {
        try {
            return client(agentBaseUrl).get()
                    .uri("/device/{serial}/apps", serial)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("获取应用列表失败: " + e.getMessage(), e);
        }
    }

    public Object uninstall(String agentBaseUrl, String serial, String packageName) {
        try {
            return client(agentBaseUrl).post()
                    .uri(uriBuilder -> uriBuilder.path("/device/{serial}/uninstall")
                            .queryParam("packageName", packageName).build(serial))
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("卸载失败: " + e.getMessage(), e);
        }
    }

    public Object appAction(String agentBaseUrl, String serial, String action, String packageName) {
        try {
            return client(agentBaseUrl).post()
                    .uri(uriBuilder -> uriBuilder.path("/device/{serial}/apps/" + action)
                            .queryParam("packageName", packageName).build(serial))
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("应用操作失败: " + e.getMessage(), e);
        }
    }

    public Object rotate(String agentBaseUrl, String serial, String direction) {
        try {
            return client(agentBaseUrl).post()
                    .uri(uriBuilder -> uriBuilder.path("/device/{serial}/rotate")
                            .queryParam("direction", direction).build(serial))
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("旋转设备失败: " + e.getMessage(), e);
        }
    }

    public Object pushFile(String agentBaseUrl, String serial, byte[] data, String filename, String remotePath) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename == null ? "upload.bin" : filename;
            }
        });
        try {
            return client(agentBaseUrl).post()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/device/{serial}/files");
                        if (remotePath != null && !remotePath.isBlank()) {
                            builder.queryParam("remotePath", remotePath);
                        }
                        return builder.build(serial);
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .body(parts)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("上传文件失败: " + e.getMessage(), e);
        }
    }

    public Object performance(String agentBaseUrl, String serial) {
        try {
            return client(agentBaseUrl).get()
                    .uri("/device/{serial}/performance", serial)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("获取性能信息失败: " + e.getMessage(), e);
        }
    }

    public Object action(String agentBaseUrl, String serial, Map<String, Object> action) {
        try {
            return client(agentBaseUrl).post()
                    .uri("/device/{serial}/action", serial)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .body(action)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("设备动作失败: " + e.getMessage(), e);
        }
    }

    public Object install(String agentBaseUrl, String serial, byte[] apk, String filename) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(apk) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "app.apk" : filename;
            }
        });
        try {
            return client(agentBaseUrl).post()
                    .uri("/device/{serial}/install?replace=true", serial)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header(AuthFilter.AGENT_TOKEN_HEADER, security.getAgentToken())
                    .body(parts)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientException e) {
            throw new AgentUnavailableException("安装 APK 失败: " + e.getMessage(), e);
        }
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        // clone() keeps the Boot-configured message converters and default headers
        // while avoiding shared mutable builder state across concurrent calls.
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
