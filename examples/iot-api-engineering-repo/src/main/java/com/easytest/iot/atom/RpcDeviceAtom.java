package com.easytest.iot.atom;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import com.easytest.engineering.RpcAtom;

import java.util.List;
import java.util.Map;

/** ATOM: generic RPC invocation; bind RpcAtom to Dubbo/Feign in the runtime bootstrap. */
public class RpcDeviceAtom {

    @EasyContract(id = "iot.atom.rpc.deviceDetail", name = "RPC 查询设备", layer = ContractLayer.ATOM,
            protocols = {"RPC"}, description = "通过 RPC 查询 device 微服务",
            inputs = {"deviceId"}, outputs = {"device"}, tags = {"iot", "rpc"})
    public ContractResult queryDevice(ContractContext context) {
        String deviceId = context.requireString("deviceId");
        com.easytest.iot.IotEngineeringRuntime.bootstrap();
        Object result = RpcAtom.invoke("iot.device.api.DeviceFacade", "getDevice",
                List.of(deviceId), Map.of("group", "iot"));
        context.output("device", result);
        return ContractResult.ok("RPC 查询完成");
    }
}
