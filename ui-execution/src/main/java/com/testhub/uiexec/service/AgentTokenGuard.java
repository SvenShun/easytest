package com.testhub.uiexec.service;

import com.testhub.common.security.AuthFilter;
import com.testhub.common.security.SecurityProperties;
import com.testhub.common.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Manual, constant-time verification of {@code X-Agent-Token} for agent
 * registration, heartbeat and callbacks. This is intentionally a second check
 * on top of the common AuthFilter (a JWT could otherwise authenticate the same
 * request).
 */
@Component
public class AgentTokenGuard {

    private final SecurityProperties securityProperties;

    public AgentTokenGuard(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public void require(HttpServletRequest request) {
        String supplied = request.getHeader(AuthFilter.AGENT_TOKEN_HEADER);
        String expected = securityProperties.getAgentToken();
        boolean valid = supplied != null && expected != null
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_AGENT_TOKEN", "Agent 令牌无效");
        }
    }

    public boolean matches(String supplied) {
        String expected = securityProperties.getAgentToken();
        return supplied != null && expected != null
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
