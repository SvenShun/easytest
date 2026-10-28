package com.testhub.uiexec.service;

import com.testhub.common.util.Ids;
import com.testhub.uiexec.client.AgentClient;
import com.testhub.uiexec.domain.AgentEntity;
import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.DeviceMetricEntity;
import com.testhub.uiexec.repository.DeviceMetricRepository;
import com.testhub.uiexec.web.dto.DeviceDtos.MetricView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceService {

    private final DeviceService devices;
    private final AgentClient agentClient;
    private final DeviceMetricRepository metrics;

    public PerformanceService(DeviceService devices, AgentClient agentClient, DeviceMetricRepository metrics) {
        this.devices = devices;
        this.agentClient = agentClient;
        this.metrics = metrics;
    }

    @Transactional
    public MetricView sample(String deviceId) {
        DeviceEntity device = devices.requireDevice(deviceId);
        AgentEntity agent = devices.requireAgent(device);
        Object raw = agentClient.performance(agent.getBaseUrl(), device.getSerial());
        Map<String, Object> map = raw instanceof Map<?, ?> values
                ? values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()), Map.Entry::getValue, (a, b) -> b))
                : Map.of();
        DeviceMetricEntity sample = new DeviceMetricEntity(Ids.newId("metric"), deviceId,
                number(map.get("cpuPercent")), number(map.get("memUsedPercent")),
                longNumber(map.get("memUsedKb")), intNumber(map.get("batteryLevel")),
                number(map.get("batteryTemperature")), Instant.now());
        metrics.save(sample);
        return view(sample);
    }

    public List<MetricView> history(String deviceId, int minutes) {
        devices.requireDevice(deviceId);
        int window = Math.max(1, Math.min(minutes <= 0 ? 60 : minutes, 24 * 60));
        return metrics.findByDeviceIdAndCreatedAtAfterOrderByCreatedAtAsc(deviceId,
                        Instant.now().minusSeconds(window * 60L)).stream()
                .map(this::view).toList();
    }

    private MetricView view(DeviceMetricEntity sample) {
        return new MetricView(sample.getId(), sample.getDeviceId(), sample.getCpuPercent(),
                sample.getMemUsedPercent(), sample.getMemUsedKb(), sample.getBatteryLevel(),
                sample.getBatteryTemperature(), sample.getCreatedAt());
    }

    private static Double number(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longNumber(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intNumber(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
