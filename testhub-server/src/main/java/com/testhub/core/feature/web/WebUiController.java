package com.testhub.core.feature.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Serves the built web console from the main service so page and API share one
 * origin. Assets are streamed explicitly (rather than via a resource handler)
 * to keep the mapping predictable across Spring Boot versions.
 */
@RestController
public class WebUiController {

    private final WebUiProperties properties;

    public WebUiController(WebUiProperties properties) {
        this.properties = properties;
    }

    @GetMapping({"/", "/index.html"})
    public ResponseEntity<byte[]> index() {
        return file(WebUiConfig.staticRoot(properties).resolve("index.html"), MediaType.TEXT_HTML)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assets/{file:.+}")
    public ResponseEntity<byte[]> asset(@PathVariable String file) {
        Path root = WebUiConfig.staticRoot(properties).resolve("assets").normalize();
        Path target = root.resolve(file).normalize();
        if (!target.startsWith(root)) {
            return ResponseEntity.notFound().build();
        }
        return file(target, mediaType(file))
                .map(response -> ResponseEntity.ok()
                        .contentType(response.getHeaders().getContentType())
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS))
                        .body(response.getBody()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> favicon() {
        return file(WebUiConfig.staticRoot(properties).resolve("favicon.ico"), MediaType.valueOf("image/x-icon"))
                .orElse(ResponseEntity.notFound().build());
    }

    private java.util.Optional<ResponseEntity<byte[]>> file(Path path, MediaType type) {
        if (!Files.isRegularFile(path)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(ResponseEntity.ok()
                    .contentType(type)
                    .cacheControl(CacheControl.noCache())
                    .body(Files.readAllBytes(path)));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private MediaType mediaType(String file) {
        String name = file.toLowerCase(Locale.ROOT);
        if (name.endsWith(".js")) return MediaType.valueOf("text/javascript");
        if (name.endsWith(".css")) return MediaType.valueOf("text/css");
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (name.endsWith(".woff2")) return MediaType.valueOf("font/woff2");
        if (name.endsWith(".woff")) return MediaType.valueOf("font/woff");
        if (name.endsWith(".json")) return MediaType.APPLICATION_JSON;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
