package com.testhub.core.feature.casemgmt;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.core.ApiTestSupport;
import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptProjectRepository;
import com.testhub.core.feature.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CapabilityTest extends ApiTestSupport {

    @Autowired
    private ScriptProjectRepository scriptRepository;

    @Test
    void caseWithoutScriptOnlySupportsAiRuntime() throws Exception {
        String token = testerToken();
        String caseId = createCase(token, "无脚本能力用例", null);

        JsonNode detail = getJson("/api/v1/cases/" + caseId, token);
        assertThat(detail.path("hasScript").asBoolean()).isFalse();
        assertThat(detail.path("script").isNull()).isTrue();
        assertThat(detail.path("capability").path("availableExecutors")).hasSize(1);
        assertThat(detail.path("capability").path("availableExecutors").get(0).asText())
                .isEqualTo("AI_RUNTIME");
        assertThat(detail.path("capability").path("defaultExecutor").asText()).isEqualTo("AI_RUNTIME");
    }

    @Test
    void caseWithScriptSupportsScriptAndAiRuntime() throws Exception {
        ScriptProject script = scriptRepository.save(new ScriptProject("script-capability",
                "login-testng.zip", ScriptType.TESTNG, "sh run.sh", "scripts/script-capability.zip",
                1024, "tester", Instant.now()));
        String token = testerToken();
        String caseId = createCase(token, "有脚本能力用例", script.getId());

        JsonNode detail = getJson("/api/v1/cases/" + caseId, token);
        assertThat(detail.path("hasScript").asBoolean()).isTrue();
        assertThat(detail.path("script").path("id").asText()).isEqualTo("script-capability");
        assertThat(detail.path("script").path("name").asText()).isEqualTo("login-testng.zip");
        assertThat(detail.path("script").path("scriptType").asText()).isEqualTo("TESTNG");
        assertThat(detail.path("script").path("entryCommand").asText()).isEqualTo("sh run.sh");
        assertThat(detail.path("capability").path("availableExecutors")).hasSize(2);
        assertThat(detail.path("capability").path("availableExecutors").get(0).asText())
                .isEqualTo("SCRIPT");
        assertThat(detail.path("capability").path("availableExecutors").get(1).asText())
                .isEqualTo("AI_RUNTIME");
        assertThat(detail.path("capability").path("defaultExecutor").asText()).isEqualTo("SCRIPT");
    }

    @Test
    void unknownScriptReferenceIsRejected() throws Exception {
        String token = testerToken();
        String body = """
                {"title":"坏脚本引用","given":"g","when":"w","then":"t","scriptId":"missing-script"}
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isNotFound());
    }
}
