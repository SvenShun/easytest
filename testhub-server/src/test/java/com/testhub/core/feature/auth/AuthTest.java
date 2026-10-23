package com.testhub.core.feature.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.Role;
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
class AuthTest extends ApiTestSupport {

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "admin");
        body.put("password", "admin123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSec").value(43_200))
                .andExpect(jsonPath("$.user.id").value("u-admin"))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        JsonNode node = json(result);
        assertThat(node.path("token").asText()).isNotBlank();
        assertThat(node.path("user").path("displayName").asText()).isEqualTo("平台管理员");
    }

    @Test
    void loginWithWrongPasswordReturnsInvalidCredentials() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "tester");
        body.put("password", "nope");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginWithUnknownUserReturnsInvalidCredentials() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "ghost");
        body.put("password", "whatever");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void jwtCanBeParsedBackToPrincipal() throws Exception {
        String token = testerToken();
        AuthPrincipal principal = jwtTokenProvider.parse(token);
        assertThat(principal.userId()).isEqualTo("u-tester");
        assertThat(principal.username()).isEqualTo("tester");
        assertThat(principal.role()).isEqualTo(Role.TESTER);
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = testerToken();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.role").value("TESTER"));
    }
}
