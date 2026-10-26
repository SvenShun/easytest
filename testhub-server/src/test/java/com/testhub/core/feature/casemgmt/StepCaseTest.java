package com.testhub.core.feature.casemgmt;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Case CRUD/validation for declarative Sonic-style step cases. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StepCaseTest extends ApiTestSupport {

    @Test
    void createAndUpdateStepCaseKeepsOrderAndCapability() throws Exception {
        String token = testerToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "登录步骤用例");
        body.put("given", "已打开 App");
        body.put("when", "按步骤操作");
        body.put("then", "看到首页");
        body.put("executionMode", "STEPS");
        body.put("steps", List.of(
                step(2, "ASSERT_TEXT", "TEXT", "首页", null, null, 5000, true),
                step(1, "CLICK", "TEXT", "登录", null, null, null, true),
                step(3, "INPUT", "ID", "username", "tester", null, null, true),
                step(4, "SWIPE", "COORD", null, "100,800,100,200", null, null, true),
                step(5, "SCREENSHOT", null, null, null, null, null, true)));

        MvcResult created = mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("STEPS"))
                .andExpect(jsonPath("$.steps.length()").value(5))
                .andExpect(jsonPath("$.steps[0].seq").value(1))
                .andExpect(jsonPath("$.steps[0].stepType").value("CLICK"))
                .andExpect(jsonPath("$.steps[1].seq").value(2))
                .andExpect(jsonPath("$.steps[1].stepType").value("ASSERT_TEXT"))
                .andExpect(jsonPath("$.steps[1].timeoutMs").value(5000))
                .andExpect(jsonPath("$.steps[2].selectorType").value("ID"))
                .andExpect(jsonPath("$.steps[2].value").value("tester"))
                .andExpect(jsonPath("$.steps[3].selectorType").value("COORD"))
                .andExpect(jsonPath("$.steps[4].stepType").value("SCREENSHOT"))
                .andExpect(jsonPath("$.capability.defaultExecutor").value("STEP"))
                .andExpect(jsonPath("$.capability.availableExecutors[0]").value("STEP"))
                .andReturn();
        String caseId = json(created).path("id").asText();

        JsonNode fetched = getJson("/api/v1/cases/" + caseId, token);
        assertThat(fetched.path("executionMode").asText()).isEqualTo("STEPS");
        assertThat(fetched.path("steps")).hasSize(5);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("title", "登录步骤用例-已更新");
        update.put("given", "g");
        update.put("when", "w");
        update.put("then", "t");
        update.put("executionMode", "STEPS");
        update.put("steps", List.of(
                step(1, "LAUNCH", "TEXT", "com.testhub.demo", null, null, null, true),
                step(2, "WAIT", null, null, "800", null, 2000, true),
                step(3, "ASSERT_EXISTS", "TEXT", "首页", null, null, null, true),
                step(4, "BACK", null, null, null, null, null, false)));

        mockMvc.perform(put("/api/v1/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("登录步骤用例-已更新"))
                .andExpect(jsonPath("$.steps.length()").value(4))
                .andExpect(jsonPath("$.steps[0].stepType").value("LAUNCH"))
                .andExpect(jsonPath("$.steps[1].stepType").value("WAIT"))
                .andExpect(jsonPath("$.steps[1].value").value("800"))
                .andExpect(jsonPath("$.steps[3].enabled").value(false));

        // Update without a steps field keeps the existing steps (backwards compatible clients).
        Map<String, Object> titleOnly = new LinkedHashMap<>();
        titleOnly.put("title", "仅改标题");
        titleOnly.put("executionMode", "STEPS");
        mockMvc.perform(put("/api/v1/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(titleOnly)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("仅改标题"))
                .andExpect(jsonPath("$.steps.length()").value(4));
    }

    @Test
    void stepModeRequiresAtLeastOneEnabledStep() throws Exception {
        String token = testerToken();
        String base = """
                {"title":"空步骤用例","given":"g","when":"w","then":"t","executionMode":"STEPS","steps":[]}
                """;
        mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base))
                .andExpect(status().isBadRequest());

        String disabled = """
                {"title":"禁用步骤用例","given":"g","when":"w","then":"t","executionMode":"STEPS",
                 "steps":[{"stepType":"CLICK","selectorType":"TEXT","selector":"登录","enabled":false}]}
                """;
        mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disabled))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidStepTypesAndCoordinatesAreRejected() throws Exception {
        String token = testerToken();
        assertBadStep(token, "{\"stepType\":\"DANCE\",\"selector\":\"x\"}");
        assertBadStep(token, "{\"stepType\":\"CLICK\"}");
        assertBadStep(token, "{\"stepType\":\"SWIPE\",\"selectorType\":\"COORD\",\"value\":\"1,2,3\"}");
        assertBadStep(token, "{\"stepType\":\"TAP\",\"selectorType\":\"COORD\",\"selector\":\"a,b\"}");
        assertBadStep(token, "{\"stepType\":\"INPUT\",\"selectorType\":\"COORD\",\"selector\":\"1,2\"}");
        assertBadStep(token, "{\"stepType\":\"WAIT\",\"value\":\"soon\"}");
        assertBadStep(token, "{\"stepType\":\"ASSERT_TEXT\"}");
    }

    @Test
    void aiModeIsStillTheDefault() throws Exception {
        String token = testerToken();
        String caseId = createCase(token, "默认 AI 用例", null);
        JsonNode detail = getJson("/api/v1/cases/" + caseId, token);
        assertThat(detail.path("executionMode").asText()).isEqualTo("AI");
        assertThat(detail.path("steps")).isEmpty();
        assertThat(detail.path("capability").path("defaultExecutor").asText()).isEqualTo("AI_RUNTIME");
    }

    private void assertBadStep(String token, String stepJson) throws Exception {
        String body = """
                {"title":"非法步骤用例","given":"g","when":"w","then":"t","executionMode":"STEPS",
                 "steps":[%s]}
                """.formatted(stepJson);
        mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> step(Integer seq, String stepType, String selectorType, String selector,
                                     String value, String assertValue, Integer timeoutMs, Boolean enabled) {
        Map<String, Object> step = new LinkedHashMap<>();
        if (seq != null) {
            step.put("seq", seq);
        }
        step.put("stepType", stepType);
        step.put("selectorType", selectorType);
        step.put("selector", selector);
        step.put("value", value);
        step.put("assertValue", assertValue);
        step.put("timeoutMs", timeoutMs);
        step.put("enabled", enabled);
        return step;
    }
}
