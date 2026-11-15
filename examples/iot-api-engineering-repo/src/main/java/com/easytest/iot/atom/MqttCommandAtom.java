package com.easytest.iot.atom;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.easytest.engineering.MqttAtom;
import com.easytest.iot.config.IotConfig;

/** ATOM: MQTT device command used to simulate IoT device/server communication. */
public class MqttCommandAtom {

    @EasyContract(id = "iot.atom.mqtt.command", name = "MQTT 设备指令", layer = ContractLayer.ATOM,
            protocols = {"MQTT"}, description = "向设备 topic 发送控制指令",
            inputs = {"deviceId", "command"}, outputs = {"topic"}, tags = {"iot", "mqtt"})
    public ContractResult sendCommand(ContractContext context) {
        String deviceId = context.requireString("deviceId");
        String command = context.requireString("command");
        String topic = IotConfig.mqttTopic("command", deviceId);
        MqttAtom.publish(IotConfig.mqttHost(), IotConfig.mqttPort(), null, topic,
                com.easytest.iot.template.RequestTemplates.deviceCommand(command), null, null, 30);
        context.output("topic", topic);
        return ContractResult.ok("MQTT 指令已发送");
    }
}
