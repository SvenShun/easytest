package com.easytest.iot.flow;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;

import com.easytest.engineering.HttpAtom;
import com.easytest.engineering.KafkaAtom;
import com.easytest.engineering.MqttAtom;
import com.easytest.engineering.RpcAtom;
import com.easytest.iot.config.IotConfig;

import java.util.List;
import java.util.Map;

/** FLOW layer: business-semantic cases; reuses action/atom operations. */
public class IotFlows {

    @EasyContract(id = "iot.flow.single.loginContract", name = "单接口：网关登录契约",
            layer = ContractLayer.FLOW, protocols = {"HTTP"}, description = "验证 gateway 登录接口能力/契约",
            inputs = {"username", "password"}, outputs = {"token"}, tags = {"iot", "single"})
    public ContractResult loginContract(ContractContext context) {
        String body = com.easytest.iot.template.RequestTemplates.login(
                context.requireString("username"), context.requireString("password"));
        HttpAtom.Response response = HttpAtom.postJson(IotConfig.gateway() + "/api/auth/login",
                body, IotConfig.jsonHeaders(), 10_000);
        if (!response.ok()) return ContractResult.fail("登录接口失败: " + response.body());
        context.output("token", response.body());
        return ContractResult.ok("单接口登录契约通过");
    }

    @EasyContract(id = "iot.flow.e2e.bindAndControl", name = "端到端：家庭-设备-控制",
            layer = ContractLayer.FLOW, protocols = {"HTTP", "MQTT", "KAFKA", "RPC"},
            description = "从网关登录开始，绑定设备并验证异步链路",
            inputs = {"username", "password", "homeId", "deviceId"}, outputs = {"token", "eventOffset"},
            tags = {"iot", "e2e"})
    public ContractResult bindAndControl(ContractContext context) {
        String username = context.requireString("username");
        String password = context.requireString("password");
        String homeId = context.requireString("homeId");
        String deviceId = context.requireString("deviceId");
        HttpAtom.Response login = HttpAtom.postJson(IotConfig.gateway() + "/api/auth/login",
                com.easytest.iot.template.RequestTemplates.login(username, password),
                IotConfig.jsonHeaders(), 10_000);
        if (!login.ok()) return ContractResult.fail("端到端登录失败");
        HttpAtom.Response bind = HttpAtom.postJson(IotConfig.gateway() + "/api/device/bind",
                com.easytest.iot.template.RequestTemplates.bindDevice(homeId, deviceId),
                Map.of("Content-Type", "application/json", "Authorization", "Bearer " + login.body()),
                10_000);
        if (!bind.ok()) return ContractResult.fail("绑定设备失败");
        MqttAtom.publish(IotConfig.mqttHost(), IotConfig.mqttPort(), null,
                IotConfig.mqttTopic("command", deviceId),
                com.easytest.iot.template.RequestTemplates.deviceCommand("ON"), null, null, 30);
        long offset = KafkaAtom.produce(IotConfig.kafka(), IotConfig.topic("deviceEvents"), deviceId,
                com.easytest.iot.template.RequestTemplates.deviceEvent("DEVICE_CONTROLLED", deviceId), List.of());
        context.output("token", login.body());
        context.output("eventOffset", offset);
        return ContractResult.ok("端到端链路通过");
    }

    @EasyContract(id = "iot.flow.single.mysqlSeed", name = "单接口：MySQL 造数与查询",
            layer = ContractLayer.FLOW, protocols = {"SQL"}, description = "单接口逻辑测清楚，用 DB 造数",
            inputs = {"homeId", "ownerId"}, outputs = {"row"}, tags = {"iot", "single", "seed"})
    public ContractResult mysqlSeed(ContractContext context) {
        var row = com.easytest.engineering.JdbcAtom.queryOne(IotConfig.mysqlUrl(), IotConfig.mysqlUser(),
                IotConfig.mysqlPassword(), "SELECT id, owner_id FROM home WHERE id = ?",
                context.requireString("homeId"));
        context.output("row", row);
        return row == null ? ContractResult.fail("造数未生效") : ContractResult.ok("DB 造数可见");
    }

    @EasyContract(id = "iot.flow.single.rpcDevice", name = "单接口：RPC 设备查询",
            layer = ContractLayer.FLOW, protocols = {"RPC"}, description = "微服务内部同步链路验证",
            inputs = {"deviceId"}, outputs = {"device"}, tags = {"iot", "single"})
    public ContractResult rpcDevice(ContractContext context) {
        com.easytest.iot.IotEngineeringRuntime.bootstrap();
        Object result = RpcAtom.invoke("iot.device.api.DeviceFacade", "getDevice",
                List.of(context.requireString("deviceId")), Map.of("group", "iot"));
        context.output("device", result);
        return ContractResult.ok("RPC 设备查询完成");
    }
}
