package com.testhub.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared MockMvc helpers for the API-level tests. */
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    protected String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("token").asText();
    }

    protected String adminToken() throws Exception {
        return login("admin", "admin123");
    }

    protected String testerToken() throws Exception {
        return login("tester", "test123");
    }

    protected String businessToken() throws Exception {
        return login("business", "biz123");
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    protected String createCase(String token, String title, String scriptId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("given", "已打开 App");
        body.put("when", "执行操作");
        body.put("then", "看到结果");
        body.put("requirementId", null);
        body.put("scriptId", scriptId);
        body.put("source", "MANUAL");
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("id").asText();
    }

    protected void review(String token, String caseId, String action) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("comment", "ok");
        mockMvc.perform(post("/api/v1/cases/{id}/review", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    protected String createApprovedCase(String token, String title) throws Exception {
        String caseId = createCase(token, title, null);
        review(token, caseId, "SUBMIT");
        review(token, caseId, "APPROVE");
        return caseId;
    }

    protected String createPlan(String token, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "自动化测试计划");
        MvcResult result = mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("id").asText();
    }

    protected void mount(String token, String planId, List<String> caseIds, String override) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseIds", caseIds);
        body.put("executorTypeOverride", override);
        mockMvc.perform(post("/api/v1/plans/{id}/cases", planId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
