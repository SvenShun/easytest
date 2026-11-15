package com.easytest.iotlocal.service;

import com.easytest.iotlocal.BindRequest;
import com.easytest.iotlocal.BindResult;
import com.easytest.iotlocal.DeviceFacade;
import com.easytest.iotlocal.DeviceInfo;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@DubboService
public class DeviceFacadeImpl implements DeviceFacade {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    public DeviceFacadeImpl(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc; this.kafka = kafka;
    }

    @Override
    public BindResult bind(BindRequest request) {
        if (request == null || request.getHomeId() == null || request.getDeviceId() == null) {
            return new BindResult(false, null, null, "homeId/deviceId required");
        }
        jdbc.update("INSERT INTO device(device_id, home_id, status) VALUES (?, ?, 'BOUND') "
                + "ON DUPLICATE KEY UPDATE home_id=VALUES(home_id), status='BOUND'",
                request.getDeviceId(), request.getHomeId());
        kafka.send("iot-device-events", request.getDeviceId(),
                "{\"event\":\"DEVICE_BOUND\",\"deviceId\":\"" + request.getDeviceId() + "\"}");
        return new BindResult(true, request.getHomeId(), request.getDeviceId(), "bound");
    }

    @Override
    public DeviceInfo getDevice(String deviceId) {
        var rows = jdbc.queryForList("SELECT device_id, home_id, status FROM device WHERE device_id = ?", deviceId);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        return new DeviceInfo(String.valueOf(row.get("device_id")), String.valueOf(row.get("home_id")),
                String.valueOf(row.get("status")));
    }
}
