package com.testhub.core.feature.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KbApiTest extends ApiTestSupport {

    @Test
    void appPageControlActionRoundTrip() throws Exception {
        String token = testerToken();
        JsonNode app = postJson("/api/v1/kb/apps", Map.of(
                "name", "知识库测试应用", "packageName", "com.testhub.kb", "platform", "android"), token);
        JsonNode page = postJson("/api/v1/kb/apps/" + app.path("id").asText() + "/pages",
                Map.of("name", "首页", "route", "HomeActivity"), token);
        JsonNode control = postJson("/api/v1/kb/pages/" + page.path("id").asText() + "/controls",
                Map.of("name", "登录按钮", "text", "登录", "resourceId", "btn_login",
                        "intent", "提交登录", "controlType", "BUTTON", "source", "MANUAL"), token);
        postJson("/api/v1/kb/controls/" + control.path("id").asText() + "/actions",
                Map.of("actionType", "CLICK", "label", "点击登录", "source", "MANUAL"), token);

        JsonNode apps = getJson("/api/v1/kb/apps", token);
        JsonNode created = null;
        for (JsonNode item : apps) {
            if (item.path("name").asText().equals("知识库测试应用")) {
                created = item;
            }
        }
        assertThat(created).isNotNull();
        assertThat(created.path("pageCount").asInt()).isEqualTo(1);
        assertThat(created.path("controlCount").asInt()).isEqualTo(1);

        JsonNode controls = getJson("/api/v1/kb/pages/" + page.path("id").asText() + "/controls", token);
        assertThat(controls).hasSize(1);
        assertThat(controls.get(0).path("actions")).hasSize(1);
        assertThat(controls.get(0).path("actions").get(0).path("actionType").asText()).isEqualTo("CLICK");
    }

    private JsonNode postJson(String path, Map<String, Object> body, String token) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }
}
