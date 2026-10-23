package com.testhub.uiexec.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the local artifact directory under {@code /artifacts/**} (no auth in V1,
 * paths contain task UUIDs; see docs/V1-SCOPE.md deviation table).
 */
@Configuration
public class ArtifactWebConfig implements WebMvcConfigurer {

    private final UiExecProperties properties;

    public ArtifactWebConfig(UiExecProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(properties.getArtifactDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ignored) {
            // The artifact store will report a clearer error if it is actually used.
        }
        registry.addResourceHandler("/artifacts/**")
                .addResourceLocations(root.toUri().toString());
    }
}
