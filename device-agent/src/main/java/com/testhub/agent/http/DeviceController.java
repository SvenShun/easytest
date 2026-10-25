package com.testhub.agent.http;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.testhub.agent.driver.ActionResult;
import com.testhub.agent.driver.DeviceAction;
import com.testhub.agent.driver.DeviceDriver;
import com.testhub.agent.driver.DeviceManager;
import com.testhub.agent.driver.UiTree;

/**
 * Local device driver endpoints used by ai-runtime (contract §3). These are
 * intentionally usable without a token from loopback.
 */
@RestController
@RequestMapping("/device")
public class DeviceController {

    private final DeviceManager deviceManager;

    public DeviceController(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    @GetMapping("/{serial}/screenshot")
    public ResponseEntity<?> screenshot(@PathVariable String serial) {
        Optional<DeviceDriver> driver = deviceManager.find(serial);
        if (driver.isEmpty()) {
            return deviceNotFound();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(driver.get().screenshot());
    }

    @GetMapping("/{serial}/ui-tree")
    public ResponseEntity<?> uiTree(@PathVariable String serial) {
        Optional<DeviceDriver> driver = deviceManager.find(serial);
        if (driver.isEmpty()) {
            return deviceNotFound();
        }
        UiTree tree = driver.get().uiTree();
        return ResponseEntity.ok(tree);
    }

    @PostMapping("/{serial}/action")
    public ResponseEntity<?> action(@PathVariable String serial,
            @RequestBody(required = false) DeviceAction action) {
        Optional<DeviceDriver> driver = deviceManager.find(serial);
        if (driver.isEmpty()) {
            return deviceNotFound();
        }
        ActionResult result = driver.get().action(action);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{serial}/install")
    public ResponseEntity<?> install(@PathVariable String serial,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean replace) {
        Optional<DeviceDriver> driver = deviceManager.find(serial);
        if (driver.isEmpty()) {
            return deviceNotFound();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "apk file is empty"));
        }
        if (file.getSize() > 200L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "apk larger than 200MB"));
        }
        try {
            ActionResult result = driver.get().installApk(file.getBytes(), replace);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{serial}/reset")
    public ResponseEntity<?> reset(@PathVariable String serial) {
        Optional<DeviceDriver> driver = deviceManager.find(serial);
        if (driver.isEmpty()) {
            return deviceNotFound();
        }
        return ResponseEntity.ok(driver.get().reset());
    }

    private ResponseEntity<Map<String, Object>> deviceNotFound() {
        return ResponseEntity.status(404).body(Map.of(
                "ok", false,
                "message", "device not found"));
    }
}
