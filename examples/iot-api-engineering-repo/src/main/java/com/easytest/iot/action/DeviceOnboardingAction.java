package com.easytest.iot.action;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.easytest.engineering.HttpAtom;
import com.easytest.engineering.KafkaAtom;
import com.easytest.engineering.MqttAtom;
import com.easytest.iot.config.IotConfig;

import java.util.List;

/** ACTION: shared multi-step device onboarding. Flow uses it with business inputs. */
public class DeviceOnboardingAction {

    @EasyContract(id = "iot.action.device.onboard", name = "设备接入组合操作", layer = ContractLayer.ACTION,
            protocols = {"HTTP", "MQTT", "KAFKA"}, description = "网关绑定设备 -> MQTT 上报 -> Kafka 事件",
            inputs = {"homeId", "deviceId", "token"}, outputs = {"bound", "eventOffset"},
            tags = {"iot", "action"})
    public ContractResult onboard(ContractContext context) {
        String homeId = context.requireString("homeId");
        String deviceId = context.requireString("deviceId");
        String token = context.requireString("token");
        String body = "{\"homeId\":\"" + homeId + "\",\"deviceId\":\"" + deviceId + "\"}";
        HttpAtom.Response response = HttpAtom.postJson(IotConfig.gateway() + "/api/device/bind",
                body, java.util.Map.of("Content-Type", "application/json", "Authorization", "Bearer " + token),
                10_000);
        if (!response.ok()) return ContractResult.fail("网关绑定设备失败 status=" + response.status());
        MqttAtom.publish(IotConfig.mqttHost(), IotConfig.mqttPort(), null,
                "iot/device/" + deviceId + "/online", "{\"status\":\"ONLINE\"}", null, null, 30);
        long offset = KafkaAtom.produce(IotConfig.kafka(), "iot-device-events", deviceId,
                "{\"event\":\"DEVICE_BOUND\",\"deviceId\":\"" + deviceId + "\"}", List.of());
        context.output("bound", true);
        context.output("eventOffset", offset);
        return ContractResult.ok("设备接入组合操作完成");
    }
}
