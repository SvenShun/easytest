package io.metersphere.uiexec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client for the TestHub device-agent protocol.
 *
 * <p>The payload is serialized explicitly to JSON: MeterSphere ships both JSON and
 * XML message converters, and sending a raw {@code Map} can otherwise be encoded
 * as XML by the default converter selection.</p>
 */
@Component
public class DeviceAgentClient {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public DeviceAgentClient(UiExecutionProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.client = RestClient.builder()
                .baseUrl(trim(properties.getAgentBaseUrl()))
                .defaultHeader("X-Agent-Token", properties.getAgentToken())
                .build();
    }

    public void dispatch(Map<String, Object> payload) {
        try {
            client.post()
                    .uri("/agent/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化设备下发报文: " + e.getMessage(), e);
        }
    }

    public void cancel(String agentTaskId) {
        client.delete().uri("/agent/tasks/{id}", agentTaskId).retrieve().toBodilessEntity();
    }

    private static String trim(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:8090" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
