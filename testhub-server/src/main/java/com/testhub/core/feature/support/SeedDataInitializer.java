package com.testhub.core.feature.support;

import com.testhub.common.security.PasswordHasher;
import com.testhub.common.security.Role;
import com.testhub.core.feature.auth.AppUser;
import com.testhub.core.feature.auth.AppUserRepository;
import com.testhub.core.feature.system.ModelConfig;
import com.testhub.core.feature.system.ModelConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Development seed data: the four default accounts and mock text/vision model
 * configurations. Idempotent, and never overwrites an existing configuration.
 */
@Component
public class SeedDataInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final ModelConfigRepository modelConfigRepository;

    public SeedDataInitializer(AppUserRepository userRepository, ModelConfigRepository modelConfigRepository) {
        this.userRepository = userRepository;
        this.modelConfigRepository = modelConfigRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        seedUser("u-admin", "admin", "admin123", "平台管理员", Role.ADMIN, now);
        seedUser("u-tester", "tester", "test123", "测试工程师", Role.TESTER, now);
        seedUser("u-business", "business", "biz123", "业务用户", Role.BUSINESS, now);
        seedUser("u-ops", "ops", "ops123", "设备运维", Role.OPS, now);

        seedModelConfig("text", "mock-text", now);
        seedModelConfig("vision", "mock-vision", now);
    }

    private void seedUser(String id, String username, String rawPassword, String displayName, Role role,
                          Instant now) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(new AppUser(id, username, PasswordHasher.hash(rawPassword), displayName, role,
                true, now));
    }

    private void seedModelConfig(String configType, String modelId, Instant now) {
        if (modelConfigRepository.existsByConfigType(configType)) {
            return;
        }
        modelConfigRepository.save(new ModelConfig("mc-" + configType, configType, "mock", "", null, modelId,
                "system", now));
    }
}
