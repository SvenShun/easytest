package com.testhub.core;

import com.testhub.common.security.Role;
import com.testhub.core.feature.auth.AppUser;
import com.testhub.core.feature.auth.AppUserRepository;
import com.testhub.core.feature.system.ModelConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContextLoadsTest {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Test
    void contextLoadsAndSeedsDefaultAccountsAndModels() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getId()).isEqualTo("u-admin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("平台管理员");

        assertThat(userRepository.findByUsername("tester")).isPresent();
        assertThat(userRepository.findByUsername("business")).isPresent();
        assertThat(userRepository.findByUsername("ops")).isPresent();

        assertThat(modelConfigRepository.findByConfigType("text").orElseThrow().getProvider())
                .isEqualTo("mock");
        assertThat(modelConfigRepository.findByConfigType("text").orElseThrow().getModelId())
                .isEqualTo("mock-text");
        assertThat(modelConfigRepository.findByConfigType("vision").orElseThrow().getModelId())
                .isEqualTo("mock-vision");
    }
}
