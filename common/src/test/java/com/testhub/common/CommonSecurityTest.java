package com.testhub.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.security.AesGcmCipher;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.JwtTokenProvider;
import com.testhub.common.security.PasswordHasher;
import com.testhub.common.security.Role;
import com.testhub.common.security.SecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonSecurityTest {

    private final SecurityProperties properties = new SecurityProperties();
    private final JwtTokenProvider provider = new JwtTokenProvider(properties, new ObjectMapper());

    @Test
    void jwtRoundTrip() {
        String token = provider.createToken(new AuthPrincipal("u1", "alice", "Alice", Role.TESTER));
        AuthPrincipal parsed = provider.parse(token);
        assertThat(parsed.userId()).isEqualTo("u1");
        assertThat(parsed.role()).isEqualTo(Role.TESTER);
    }

    @Test
    void jwtRejectsTampering() {
        String token = provider.createToken(new AuthPrincipal("u1", "alice", "Alice", Role.ADMIN));
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> provider.parse(tampered)).hasMessageContaining("签名");
    }

    @Test
    void passwordHashVerifies() {
        String stored = PasswordHasher.hash("secret-123");
        assertThat(PasswordHasher.verify("secret-123", stored)).isTrue();
        assertThat(PasswordHasher.verify("wrong", stored)).isFalse();
    }

    @Test
    void aesGcmRoundTrip() {
        String encrypted = AesGcmCipher.encrypt("sk-abc", "key-material");
        assertThat(encrypted).doesNotContain("sk-abc");
        assertThat(AesGcmCipher.decrypt(encrypted, "key-material")).isEqualTo("sk-abc");
    }
}
