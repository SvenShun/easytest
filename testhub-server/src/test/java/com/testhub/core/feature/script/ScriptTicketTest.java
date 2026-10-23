package com.testhub.core.feature.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.web.ApiException;
import com.testhub.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScriptTicketTest extends ApiTestSupport {

    @Autowired
    private ScriptTicketService ticketService;

    @Test
    void uploadedScriptCanBeDownloadedWithTicket() throws Exception {
        String token = testerToken();
        byte[] zip = sampleZip();

        MvcResult upload = mockMvc.perform(multipart("/api/v1/scripts")
                        .file(new MockMultipartFile("file", "login-testng.zip", "application/zip", zip))
                        .param("name", "login-testng.zip")
                        .param("scriptType", "TESTNG")
                        .param("entryCommand", "sh run.sh")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.scriptType").value("TESTNG"))
                .andExpect(jsonPath("$.entryCommand").value("sh run.sh"))
                .andExpect(jsonPath("$.sizeBytes").value(zip.length))
                .andReturn();
        JsonNode script = json(upload);
        String scriptId = script.path("id").asText();

        String ticket = ticketService.issueForScript(scriptId);
        assertThat(ticket).contains(".");
        assertThat(ticketService.bundleUrl(scriptId))
                .startsWith("http://127.0.0.1:8080/internal/v1/scripts/" + scriptId + "/bundle?ticket=");

        MvcResult bundle = mockMvc.perform(get("/internal/v1/scripts/{id}/bundle", scriptId)
                        .param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn();
        assertThat(bundle.getResponse().getContentAsByteArray()).isEqualTo(zip);
    }

    @Test
    void expiredTicketIsRejected() throws Exception {
        String token = testerToken();
        byte[] zip = sampleZip();
        MvcResult upload = mockMvc.perform(multipart("/api/v1/scripts")
                        .file(new MockMultipartFile("file", "expired.zip", "application/zip", zip))
                        .param("name", "expired.zip")
                        .param("scriptType", "CUSTOM")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String scriptId = json(upload).path("id").asText();

        String expired = ticketService.issue(scriptId, Duration.ofSeconds(-5));
        mockMvc.perform(get("/internal/v1/scripts/{id}/bundle", scriptId).param("ticket", expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TICKET_EXPIRED"));

        assertThatThrownBy(() -> ticketService.verify(scriptId, expired))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TICKET_EXPIRED"));
    }

    @Test
    void ticketIsBoundToScriptId() throws Exception {
        String token = testerToken();
        byte[] zip = sampleZip();
        MvcResult upload = mockMvc.perform(multipart("/api/v1/scripts")
                        .file(new MockMultipartFile("file", "bound.zip", "application/zip", zip))
                        .param("name", "bound.zip")
                        .param("scriptType", "PYTEST")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String scriptId = json(upload).path("id").asText();

        String otherScriptTicket = ticketService.issue("some-other-script", Duration.ofMinutes(5));
        mockMvc.perform(get("/internal/v1/scripts/{id}/bundle", scriptId)
                        .param("ticket", otherScriptTicket))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TICKET"));

        assertThatThrownBy(() -> ticketService.verify(scriptId, otherScriptTicket))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_TICKET"));

        assertThatThrownBy(() -> ticketService.verify(scriptId, "not-a-ticket"))
                .isInstanceOf(ApiException.class);

        String valid = ticketService.issueForScript(scriptId);
        assertThatCode(() -> ticketService.verify(scriptId, valid)).doesNotThrowAnyException();
    }

    @Test
    void executionContextCarriesTicketSignedBundleUrl() throws Exception {
        String token = testerToken();
        byte[] zip = sampleZip();
        MvcResult upload = mockMvc.perform(multipart("/api/v1/scripts")
                        .file(new MockMultipartFile("file", "context.zip", "application/zip", zip))
                        .param("name", "context.zip")
                        .param("scriptType", "TESTNG")
                        .param("entryCommand", "sh run.sh")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode script = json(upload);
        String scriptId = script.path("id").asText();

        MvcResult caseResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/cases")
                                .header("Authorization", "Bearer " + token)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"脚本执行上下文用例","given":"g","when":"w","then":"t",
                                         "scriptId":"%s"}
                                        """.formatted(scriptId)))
                .andExpect(status().isOk())
                .andReturn();
        String caseId = json(caseResult).path("id").asText();
        review(token, caseId, "SUBMIT");
        review(token, caseId, "APPROVE");

        MvcResult context = mockMvc.perform(get("/internal/v1/cases/{id}/execution-context", caseId)
                        .header("X-Service-Token", "testhub-dev-service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.script.id").value(scriptId))
                .andExpect(jsonPath("$.script.scriptType").value("TESTNG"))
                .andExpect(jsonPath("$.script.entryCommand").value("sh run.sh"))
                .andReturn();
        JsonNode contextJson = json(context);
        String bundleUrl = contextJson.path("script").path("bundleUrl").asText();
        assertThat(bundleUrl).startsWith(
                "http://127.0.0.1:8080/internal/v1/scripts/" + scriptId + "/bundle?ticket=");
        String ticket = bundleUrl.substring(bundleUrl.indexOf("ticket=") + "ticket=".length());

        MvcResult bundle = mockMvc.perform(get("/internal/v1/scripts/{id}/bundle", scriptId)
                        .param("ticket", ticket))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(bundle.getResponse().getContentAsByteArray()).isEqualTo(zip);

        mockMvc.perform(get("/internal/v1/cases/{id}/execution-context", caseId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_TOKEN_REQUIRED"));
    }

    @Test
    void nonZipUploadIsRejected() throws Exception {
        String token = testerToken();
        mockMvc.perform(multipart("/api/v1/scripts")
                        .file(new MockMultipartFile("file", "script.txt", "text/plain",
                                "hello".getBytes(StandardCharsets.UTF_8)))
                        .param("name", "script.txt")
                        .param("scriptType", "CUSTOM")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    private byte[] sampleZip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("run.sh"));
            zip.write("echo testhub\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
