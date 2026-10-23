package com.testhub.core.feature.script;

import com.testhub.common.security.SecurityProperties;
import com.testhub.common.web.ApiException;
import com.testhub.core.config.CoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Short-lived HMAC tickets for script bundle downloads:
 * {@code ticket = "<expEpochSecond>.<base64url(hmacSha256(serviceToken, scriptId + "." + exp))>"}.
 * Agents download the bundle without a JWT, so the ticket is the only guard.
 */
@Service
public class ScriptTicketService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final CoreProperties coreProperties;

    public ScriptTicketService(SecurityProperties securityProperties, CoreProperties coreProperties) {
        this.secret = securityProperties.getServiceToken().getBytes(StandardCharsets.UTF_8);
        this.coreProperties = coreProperties;
    }

    public String issue(String scriptId, Duration ttl) {
        long expiresAt = Instant.now().plus(ttl).getEpochSecond();
        return expiresAt + "." + sign(scriptId, expiresAt);
    }

    public String issueForScript(String scriptId) {
        return issue(scriptId, coreProperties.getTicketTtl());
    }

    public void verify(String scriptId, String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw invalid();
        }
        String[] parts = ticket.split("\\.");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalid();
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TICKET_EXPIRED", "脚本下载票据已过期");
        }
        byte[] expected = sign(scriptId, expiresAt).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw invalid();
        }
    }

    public String bundleUrl(String scriptId) {
        String base = coreProperties.getInternalBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:8080";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/internal/v1/scripts/" + scriptId + "/bundle?ticket=" + issueForScript(scriptId);
    }

    private String sign(String scriptId, long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((scriptId + "." + expiresAt).getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("脚本票据签名失败", e);
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TICKET", "脚本下载票据无效");
    }
}
