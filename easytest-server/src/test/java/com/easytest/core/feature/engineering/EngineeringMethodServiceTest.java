package com.easytest.core.feature.engineering;

import com.easytest.core.feature.engineering.EngineeringMethodDtos.ManifestImportRequest;
import com.easytest.core.feature.plan.ApiExecutionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineeringMethodServiceTest {

    @Test
    void importsManifestAndUpsertsMethods() {
        EngineeringMethodRepository repository = mock(EngineeringMethodRepository.class);
        ApiExecutionClient client = mock(ApiExecutionClient.class);
        when(repository.findByMethodPath(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll()).thenReturn(List.of());
        EngineeringMethodService service = new EngineeringMethodService(repository, client, new ObjectMapper());
        String manifest = """
                schemaVersion: 1
                methods:
                  - id: engineering.login
                    name: Login
                    description: code login
                    methodPath: com.example.Login#login
                    inputs: [username]
                    outputs: [token]
                    tags: [engineering]
                """;
        var result = service.importManifest(new ManifestImportRequest(manifest));
        assertThat(result.imported()).isEqualTo(1);
        ArgumentCaptor<EngineeringMethod> captor = ArgumentCaptor.forClass(EngineeringMethod.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMethodPath()).isEqualTo("com.example.Login#login");
        assertThat(captor.getValue().getSource()).isEqualTo("manifest");
    }

    @Test
    void syncsAgentMethods() {
        EngineeringMethodRepository repository = mock(EngineeringMethodRepository.class);
        ApiExecutionClient client = mock(ApiExecutionClient.class);
        when(client.listEngineeringMethods()).thenReturn(List.of(
                new ApiExecutionClient.EngineeringMethod("id", "Name", "Desc", "a#b",
                        List.of("x"), List.of("y"), List.of("tag"), "FLOW", List.of())));
        when(repository.findByMethodPath(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll()).thenReturn(List.of());
        var service = new EngineeringMethodService(repository, client, new ObjectMapper());
        var result = service.syncFromAgents();
        assertThat(result.imported()).isEqualTo(1);
    }
}
