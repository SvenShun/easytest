package com.testhub.uiexec.web;

import com.testhub.uiexec.artifact.ArtifactStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Public read route for artifacts (screenshots, JUnit XML, failure reports,
 * logs). It streams through the configured {@link ArtifactStore}, so switching
 * to S3/MinIO does not change the URLs stored in tasks and reports.
 *
 * <p>V1 keeps this route unauthenticated; paths contain random task ids. A
 * signed URL / permission check is the documented production follow-up.
 */
@RestController
public class ArtifactController {

    private final ArtifactStore artifactStore;

    public ArtifactController(ArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
    }

    @GetMapping("/artifacts/**")
    public ResponseEntity<byte[]> get(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String relative = URLDecoder.decode(
                uri.substring(uri.indexOf("/artifacts/") + "/artifacts/".length()), StandardCharsets.UTF_8);
        if (relative.isBlank() || relative.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = artifactStore.read(relative);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(mediaType(relative))
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(content);
    }

    private MediaType mediaType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (lower.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        }
        if (lower.endsWith(".zip")) {
            return MediaType.parseMediaType("application/zip");
        }
        if (lower.endsWith(".html")) {
            return MediaType.TEXT_HTML;
        }
        if (lower.endsWith(".log") || lower.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
