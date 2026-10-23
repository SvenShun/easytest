package com.testhub.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.web.ApiException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal HS256 JWT implementation (JDK only, no JWT library). The token is
 * signed by testhub-server and verified by every service with the shared
 * secret, which keeps V1 dependency-light while preserving real auth.
 */
public class JwtTokenProvider {

    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(SecurityProperties properties, ObjectMapper objectMapper) {
        this.secret = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        this.ttl = properties.getTokenTtl();
        this.objectMapper = objectMapper;
    }

    public String createToken(AuthPrincipal principal) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", principal.userId());
        claims.put("username", principal.username());
        claims.put("name", principal.displayName());
        claims.put("role", principal.role().name());
        claims.put("iat", now);
        claims.put("exp", now + ttl.toSeconds());
        try {
            String header = ENCODER.encodeToString(HEADER.getBytes(StandardCharsets.UTF_8));
            String payload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            return signingInput + "." + ENCODER.encodeToString(hmac(signingInput));
        } catch (Exception e) {
            throw new IllegalStateException("failed to create token", e);
        }
    }

    @SuppressWarnings("unchecked")
    public AuthPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "缺少访问令牌");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "访问令牌格式不正确");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput);
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "访问令牌签名不正确");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "访问令牌签名不正确");
        }
        try {
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), Map.class);
            long exp = ((Number) claims.getOrDefault("exp", 0)).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "访问令牌已过期，请重新登录");
            }
            return new AuthPrincipal(
                    String.valueOf(claims.get("sub")),
                    String.valueOf(claims.get("username")),
                    String.valueOf(claims.getOrDefault("name", claims.get("username"))),
                    Role.valueOf(String.valueOf(claims.getOrDefault("role", "TESTER"))));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "访问令牌内容不合法");
        }
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign token", e);
        }
    }
}
