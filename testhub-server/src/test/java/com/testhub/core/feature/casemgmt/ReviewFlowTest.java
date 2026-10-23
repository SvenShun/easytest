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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFlowTest extends ApiTestSupport {

    private MvcResult reviewRequest(String token, String caseId, String action, String comment)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("comment", comment);
        return mockMvc.perform(post("/api/v1/cases/{id}/review", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    @Test
    void legalTransitionsUpdateStatusAndRecordReviews() throws Exception {
        String token = testerToken();
        String caseId = createCase(token, "评审流转用例", null);

        JsonNode created = getJson("/api/v1/cases/" + caseId, token);
        assertThat(created.path("reviewStatus").asText()).isEqualTo("DRAFT");

        reviewRequest(token, caseId, "SUBMIT", "请评审");
        JsonNode pending = getJson("/api/v1/cases/" + caseId, token);
        assertThat(pending.path("reviewStatus").asText()).isEqualTo("PENDING");

        reviewRequest(token, caseId, "APPROVE", "通过");
        JsonNode approved = getJson("/api/v1/cases/" + caseId, token);
        assertThat(approved.path("reviewStatus").asText()).isEqualTo("APPROVED");

        JsonNode reviews = getJson("/api/v1/cases/" + caseId + "/reviews", token);
        assertThat(reviews).hasSize(2);
        assertThat(reviews.get(0).path("action").asText()).isEqualTo("SUBMIT");
        assertThat(reviews.get(1).path("action").asText()).isEqualTo("APPROVE");
        assertThat(reviews.get(1).path("actor").asText()).isEqualTo("tester");
        assertThat(reviews.get(1).path("comment").asText()).isEqualTo("通过");
    }

    @Test
    void illegalTransitionsReturnConflict() throws Exception {
        String token = testerToken();
        String caseId = createCase(token, "非法流转用例", null);

        mockMvc.perform(post("/api/v1/cases/{id}/review", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REVIEW_TRANSITION"));

        reviewRequest(token, caseId, "SUBMIT", null);
        reviewRequest(token, caseId, "APPROVE", null);

        mockMvc.perform(post("/api/v1/cases/{id}/review", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REVIEW_TRANSITION"));
    }

    @Test
    void rejectThenResubmitIsAllowed() throws Exception {
        String token = testerToken();
        String caseId = createCase(token, "驳回后重提用例", null);

        reviewRequest(token, caseId, "SUBMIT", null);
        mockMvc.perform(post("/api/v1/cases/{id}/review", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"comment\":\"步骤不完整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));

        reviewRequest(token, caseId, "SUBMIT", "已补充");
        JsonNode pendingAgain = getJson("/api/v1/cases/" + caseId, token);
        assertThat(pendingAgain.path("reviewStatus").asText()).isEqualTo("PENDING");
    }

    @Test
    void permissionsFollowDefaultDeny() throws Exception {
        String business = businessToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "业务用户创建的用例");
        body.put("given", "g");
        body.put("when", "w");
        body.put("then", "t");

        mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + business)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/cases").header("Authorization", "Bearer " + business))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanReview() throws Exception {
        String token = adminToken();
        String caseId = createCase(token, "管理员评审用例", null);
        reviewRequest(token, caseId, "SUBMIT", null);
        mockMvc.perform(post("/api/v1/cases/{id}/review", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"comment\":\"admin ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));
    }
}
