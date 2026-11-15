package com.easytest.core.feature.engineeringgit;

import com.easytest.core.feature.engineering.EngineeringMethod;
import com.easytest.core.feature.engineering.EngineeringMethodRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineeringRepoScannerTest {

    @TempDir
    Path temp;

    @Test
    void scansApiManifestIntoMethodRegistry() throws Exception {
        Path repoDir = temp.resolve("api-repo");
        Files.createDirectories(repoDir);
        Files.writeString(repoDir.resolve("easytest.api.yaml"), """
                schemaVersion: 1
                repoType: api
                methods:
                  - id: git.login
                    name: Git 登录
                    description: demo
                    methodPath: com.example.GitCases#login
                    inputs: [username]
                    outputs: [token]
                    tags: [git]
                """, StandardCharsets.UTF_8);
        git(repoDir, "init", "-b", "main");
        git(repoDir, "config", "user.email", "test@example.com");
        git(repoDir, "config", "user.name", "Test");
        git(repoDir, "add", ".");
        git(repoDir, "commit", "-m", "demo");

        EngineeringMethodRepository methods = mock(EngineeringMethodRepository.class);
        when(methods.findByMethodPath(any())).thenReturn(Optional.empty());
        when(methods.findByRepoId(any())).thenReturn(List.of());
        com.easytest.core.feature.casemgmt.ClientCaseRepository cases =
                mock(com.easytest.core.feature.casemgmt.ClientCaseRepository.class);
        EngineeringRepoScanner scanner = new EngineeringRepoScanner(methods, new ObjectMapper(), cases);
        EngineeringRepo repo = new EngineeringRepo("erepo-test", "default", "demo", "API",
                repoDir.toUri().toString(), "main", ".", "tester", Instant.now());

        var result = scanner.scan(repo);
        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<EngineeringMethod> captor = ArgumentCaptor.forClass(EngineeringMethod.class);
        verify(methods).save(captor.capture());
        assertThat(captor.getValue().getMethodPath()).isEqualTo("com.example.GitCases#login");
        assertThat(captor.getValue().getRepoId()).isEqualTo("erepo-test");
        assertThat(captor.getValue().getKind()).isEqualTo("API");
    }

    private void git(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException(output);
        }
    }
}
