package com.easytest.core.feature.caselink;

import com.easytest.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseAutomationLinkTest extends ApiTestSupport {

    @Test
    void linksApiCaseAndRejectsDuplicates() throws Exception {
        String token = testerToken();
        String textCase = createApprovedCase(token, "文本登录用例");
        String apiCase = createApiCase(token, "登录接口组件");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", "API_CASE");
        body.put("targetId", apiCase);
        String linkJson = mockMvc.perform(post("/api/v1/cases/{id}/automation-links", textCase)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetName").value("登录接口组件"))
                .andExpect(jsonPath("$.targetEnabled").value(true))
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkJson).path("id").asText();

        assertThat(getJson("/api/v1/cases/" + textCase + "/automation-links", token))
                .hasSize(1);

        mockMvc.perform(post("/api/v1/cases/{id}/automation-links", textCase)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINK_EXISTS"));

        mockMvc.perform(delete("/api/v1/cases/{caseId}/automation-links/{linkId}", textCase, linkId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/{id}/automation-links", textCase)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsUnknownTargetAndUnknownCase() throws Exception {
        String token = testerToken();
        String textCase = createApprovedCase(token, "文本用例");
        Map<String, Object> body = Map.of("targetType", "API_CASE", "targetId", "missing-case");
        mockMvc.perform(post("/api/v1/cases/{id}/automation-links", textCase)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/cases/missing/automation-links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String createApiCase(String token, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("method", "GET");
        body.put("baseUrl", "http://127.0.0.1:8080");
        body.put("path", "/actuator/health");
        body.put("headers", Map.of());
        body.put("query", Map.of());
        body.put("body", null);
        body.put("assertions", List.of(Map.of("type", "STATUS_CODE", "operator", "EQ", "expected", "200")));
        body.put("inputContract", Map.of("token", "登录 token"));
        body.put("outputContract", Map.of("userId", "响应 user.id"));
        body.put("timeoutSec", 30);
        body.put("enabled", true);
        String json = mockMvc.perform(post("/api/v1/api-cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("id").asText();
    }
}
