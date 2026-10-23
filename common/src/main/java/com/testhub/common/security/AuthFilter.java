package com.testhub.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.web.ApiError;
import com.testhub.common.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Identity layer for both callers: device agents authenticate with a shared
 * agent token, service-to-service calls with the service token, everything else
 * with a JWT. Authorization itself is enforced by {@link RoleInterceptor}.
 */
@Component
@Order(10)
public class AuthFilter extends OncePerRequestFilter {

    public static final String AGENT_TOKEN_HEADER = "X-Agent-Token";
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final JwtTokenProvider tokenProvider;
    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;

    public AuthFilter(JwtTokenProvider tokenProvider, SecurityProperties properties, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String serviceToken = request.getHeader(SERVICE_TOKEN_HEADER);
            String agentToken = request.getHeader(AGENT_TOKEN_HEADER);
            String authorization = request.getHeader("Authorization");
            if (serviceToken != null && matches(serviceToken, properties.getServiceToken())) {
                CurrentUser.set(AuthPrincipal.service());
            } else if (agentToken != null) {
                if (!matches(agentToken, properties.getAgentToken())) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_AGENT_TOKEN", "Agent 令牌无效");
                }
                CurrentUser.set(new AuthPrincipal("agent", "device-agent", "Device Agent", Role.OPS));
            } else if (authorization != null && authorization.startsWith("Bearer ")) {
                CurrentUser.set(tokenProvider.parse(authorization.substring(7).trim()));
            }
            chain.doFilter(request, response);
        } catch (ApiException ex) {
            writeError(response, ex);
        } finally {
            CurrentUser.clear();
        }
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        response.setStatus(ex.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(ex.status().value(), ex.code(), ex.getMessage(), null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean matches(String supplied, String expected) {
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
