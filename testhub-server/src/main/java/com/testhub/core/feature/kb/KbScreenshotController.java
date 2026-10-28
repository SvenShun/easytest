package com.testhub.core.feature.kb;

import com.testhub.core.feature.kb.KbDtos.ScreenshotView;
import com.testhub.core.feature.support.ArtifactStore;
import com.testhub.common.util.Ids;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/kb/screenshots")
public class KbScreenshotController {

    private final ArtifactStore artifacts;

    public KbScreenshotController(ArtifactStore artifacts) {
        this.artifacts = artifacts;
    }

    @PostMapping
    @com.testhub.common.security.RequireRole({com.testhub.common.security.Role.ADMIN,
            com.testhub.common.security.Role.TESTER})
    public ScreenshotView upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw com.testhub.common.web.ApiException.badRequest("截图文件为空");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw com.testhub.common.web.ApiException.badRequest("截图不能超过 10MB");
        }
        String name = file.getOriginalFilename() == null ? "screen.png" : file.getOriginalFilename();
        String ext = name.toLowerCase(Locale.ROOT).endsWith(".jpg") || name.toLowerCase(Locale.ROOT).endsWith(".jpeg")
                ? ".jpg" : ".png";
        String key = "kb/" + Ids.newId("shot") + ext;
        artifacts.put(key, file.getBytes());
        return new ScreenshotView("/api/v1/kb/screenshots/" + key.substring("kb/".length()));
    }

    @GetMapping("/{name:.+}")
    @com.testhub.common.security.PublicApi
    public ResponseEntity<byte[]> get(@PathVariable String name) {
        String key = "kb/" + name;
        byte[] content = artifacts.get(key);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = name.toLowerCase(Locale.ROOT).endsWith(".jpg") ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
        return ResponseEntity.ok().contentType(type).body(content);
    }
}
