package com.testhub.core.feature.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.core.ApiTestSupport;
import com.testhub.core.feature.system.ModelConfig;
import com.testhub.core.feature.system.ModelConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiMockProviderTest extends ApiTestSupport {

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Test
    void loginRequirementProducesDeterministicBddDraft() throws Exception {
        String token = testerToken();
        String body = """
                {"requirementId":null,"requirementText":"用户可以使用用户名密码登录","sampleCount":3}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/ai/bdd-drafts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.modelId").value("mock-text"))
                .andExpect(jsonPath("$.drafts.length()").value(1))
                .andExpect(jsonPath("$.drafts[0].reviewStatus").value("DRAFT"))
                .andReturn();

        JsonNode response = json(result);
        JsonNode draft = response.path("drafts").get(0);
        assertThat(draft.path("caseId").asText()).isNotBlank();
        assertThat(draft.path("title").asText()).isEqualTo("用户使用正确凭据登录");
        assertThat(draft.path("given").asText()).isEqualTo("已打开 App 且未登录");
        assertThat(draft.path("when").asText()).isEqualTo("输入用户名 tester 和密码 secret 并点击登录");
        assertThat(draft.path("then").asText()).isEqualTo("进入首页并显示 登录成功");

        String caseId = draft.path("caseId").asText();
        JsonNode caseDetail = getJson("/api/v1/cases/" + caseId, token);
        assertThat(caseDetail.path("source").asText()).isEqualTo("AI");
        assertThat(caseDetail.path("reviewStatus").asText()).isEqualTo("DRAFT");
        assertThat(caseDetail.path("modelId").asText()).isEqualTo("mock-text");

        String jobId = response.path("jobId").asText();
        JsonNode job = getJson("/api/v1/ai/jobs/" + jobId, token);
        assertThat(job.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(job.path("modelId").asText()).isEqualTo("mock-text");
        assertThat(job.path("promptVersion").asText()).isEqualTo("v1");
        assertThat(job.path("outputCaseIds").get(0).asText()).isEqualTo(caseId);
    }

    @Test
    void genericRequirementProducesNonEmptyDraft() throws Exception {
        String token = testerToken();
        MvcResult result = mockMvc.perform(post("/api/v1/ai/bdd-drafts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementText\":\"用户可以修改个人资料\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode draft = json(result).path("drafts").get(0);
        assertThat(draft.path("title").asText()).isEqualTo("用户可以修改个人资料");
        assertThat(draft.path("given").asText()).isNotBlank();
        assertThat(draft.path("when").asText()).isNotBlank();
        assertThat(draft.path("then").asText()).isNotBlank();
    }

    @Test
    void missingTextModelConfigurationReturnsConflict() throws Exception {
        String token = testerToken();
        ModelConfig config = modelConfigRepository.findByConfigType("text").orElseThrow();
        String originalProvider = config.getProvider();
        String originalModelId = config.getModelId();
        config.setProvider(null);
        config.setModelId(null);
        modelConfigRepository.saveAndFlush(config);
        try {
            mockMvc.perform(post("/api/v1/ai/bdd-drafts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requirementText\":\"任何需求\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MODEL_NOT_CONFIGURED"));
        } finally {
            config.setProvider(originalProvider);
            config.setModelId(originalModelId);
            modelConfigRepository.saveAndFlush(config);
        }
    }

    @Test
    void historySamplesAreReadableThroughJobsList() throws Exception {
        String token = testerToken();
        mockMvc.perform(post("/api/v1/ai/bdd-drafts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementText\":\"用户可以使用用户名密码登录\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ai/jobs")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
