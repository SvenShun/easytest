package com.testhub.agent.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.agent.config.AgentProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces {@code X-Agent-Token}:
 * <ul>
 *   <li>all {@code /agent/**} endpoints except {@code /agent/health};</li>
 *   <li>{@code /device/**} only for non-loopback callers (local Runtime/ops needs no token).</li>
 * </ul>
 */
@Component
public class AgentTokenInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Agent-Token";

    private final AgentProperties properties;
    private final ObjectMapper mapper;

    public AgentTokenInterceptor(AgentProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        if ("/agent/health".equals(path)) {
            return true;
        }
        boolean devicePath = path.startsWith("/device/");
        if (devicePath && isLoopback(request.getRemoteAddr())) {
            return true;
        }
        String provided = request.getHeader(TOKEN_HEADER);
        if (matches(provided)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(),
                ApiError.of(401, "UNAUTHORIZED", "invalid or missing X-Agent-Token", path));
        return false;
    }

    private boolean matches(String provided) {
        if (provided == null) {
            return false;
        }
        String expected = properties.getToken();
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    static boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        return "127.0.0.1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || remoteAddr.startsWith("127.");
    }
}
