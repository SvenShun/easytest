package com.easytest.iot.atom;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.easytest.engineering.KafkaAtom;
import com.easytest.iot.config.IotConfig;

import java.util.List;

/** ATOM: Kafka async-link event publish/assert. */
public class KafkaEventAtom {

    @EasyContract(id = "iot.atom.kafka.publish", name = "发送 Kafka 设备事件", layer = ContractLayer.ATOM,
            protocols = {"KAFKA"}, description = "向设备事件 topic 发送异步消息",
            inputs = {"key", "value"}, outputs = {"offset"}, tags = {"iot", "kafka"})
    public ContractResult publish(ContractContext context) {
        String key = context.requireString("key");
        String value = context.requireString("value");
        long offset = KafkaAtom.produce(IotConfig.kafka(), "iot-device-events", key, value, List.of());
        context.output("offset", offset);
        return ContractResult.ok("Kafka 消息已发送");
    }

    @EasyContract(id = "iot.atom.kafka.consume", name = "消费 Kafka 设备事件", layer = ContractLayer.ATOM,
            protocols = {"KAFKA"}, description = "消费设备事件用于端到端断言",
            inputs = {"topic"}, outputs = {"messages"}, tags = {"iot", "kafka"})
    public ContractResult consume(ContractContext context) {
        String topic = context.input("topic") == null ? "iot-device-events" : context.requireString("topic");
        context.output("messages", KafkaAtom.consume(IotConfig.kafka(), topic, null, 1, 10_000));
        return ContractResult.ok("Kafka 消费完成");
    }
}
