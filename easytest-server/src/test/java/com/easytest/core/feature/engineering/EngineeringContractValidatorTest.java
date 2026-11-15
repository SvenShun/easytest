package com.easytest.core.feature.engineering;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineeringContractValidatorTest {

    @Test
    void checksMethodAndRequiredInputs() {
        EngineeringMethodRepository repository = mock(EngineeringMethodRepository.class);
        EngineeringMethod method = new EngineeringMethod("id", "login", "Login", "desc",
                "com.example.Login#login", "[\"username\"]", "[\"token\"]", "[]", "agent",
                java.time.Instant.now());
        when(repository.findByMethodPath("com.example.Login#login")).thenReturn(Optional.of(method));
        EngineeringContractValidator validator = new EngineeringContractValidator(repository);
        String valid = """
                steps:
                  - name: login
                    type: component
                    methodPath: com.example.Login#login
                    inputs: {username: tester}
                """;
        assertThat(validator.validate(valid)).isEmpty();

        String missingInput = """
                steps:
                  - name: login
                    type: component
                    methodPath: com.example.Login#login
                    inputs: {}
                """;
        assertThat(validator.validate(missingInput)).anyMatch(error -> error.contains("username"));

        String missingMethod = """
                steps:
                  - name: login
                    type: component
                    methodPath: com.example.Missing#login
                """;
        assertThat(validator.validate(missingMethod)).anyMatch(error -> error.contains("未注册"));
    }
}
