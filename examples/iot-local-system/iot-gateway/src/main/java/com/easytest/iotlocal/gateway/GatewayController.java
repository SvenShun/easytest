package com.easytest.iotlocal.gateway;

import com.easytest.iotlocal.BindRequest;
import com.easytest.iotlocal.BindResult;
import com.easytest.iotlocal.DeviceFacade;
import com.easytest.iotlocal.DeviceInfo;
import com.easytest.iotlocal.LoginRequest;
import com.easytest.iotlocal.LoginResult;
import com.easytest.iotlocal.UserFacade;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GatewayController {

    @DubboReference(url = "dubbo://127.0.0.1:20880")
    private UserFacade userFacade;

    @DubboReference(url = "dubbo://127.0.0.1:20880")
    private DeviceFacade deviceFacade;

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        LoginResult result = userFacade.login(new LoginRequest(str(body.get("username")),
                str(body.get("password"))));
        if (!result.isOk()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid credentials"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", result.getToken());
        response.put("userId", result.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/device/bind")
    public ResponseEntity<Map<String, Object>> bind(@RequestBody Map<String, Object> body) {
        BindResult result = deviceFacade.bind(new BindRequest(str(body.get("homeId")), str(body.get("deviceId"))));
        if (!result.isOk()) return ResponseEntity.badRequest().body(Map.of("error", result.getMessage()));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("homeId", result.getHomeId());
        response.put("deviceId", result.getDeviceId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<?> device(@PathVariable String deviceId) {
        DeviceInfo info = deviceFacade.getDevice(deviceId);
        if (info == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("deviceId", info.getDeviceId(), "homeId", info.getHomeId(),
                "status", info.getStatus()));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
