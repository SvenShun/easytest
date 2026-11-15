package com.easytest.apiexec.contract;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Service-token endpoint used by core to install Git-engineering JARs. */
@RestController
@RequestMapping("/api/v1/api-execution/engineering")
public class EngineeringArtifactController {

    private final EngineeringMethodRegistry registry;

    public EngineeringArtifactController(EngineeringMethodRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("file 不能为空");
        registry.installJar(file.getOriginalFilename(), file.getBytes());
        return Map.of("installed", true, "methods", registry.list().size());
    }
}
