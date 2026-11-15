package com.easytest.iot;

import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractLayer;
import com.easytest.contract.EasyContract;
import com.easytest.iot.atom.RpcDeviceAtom;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IotLayerContractTest {

    @Test
    void atomAndActionAndFlowAreAnnotated() throws Exception {
        Method atom = Class.forName("com.easytest.iot.atom.HttpGatewayAtom").getMethod("login", ContractContext.class);
        Method action = Class.forName("com.easytest.iot.action.DeviceOnboardingAction")
                .getMethod("onboard", ContractContext.class);
        Method flow = Class.forName("com.easytest.iot.flow.IotFlows")
                .getMethod("bindAndControl", ContractContext.class);
        assertThat(atom.getAnnotation(EasyContract.class).layer()).isEqualTo(ContractLayer.ATOM);
        assertThat(action.getAnnotation(EasyContract.class).layer()).isEqualTo(ContractLayer.ACTION);
        assertThat(flow.getAnnotation(EasyContract.class).layer()).isEqualTo(ContractLayer.FLOW);
        assertThat(atom.getAnnotation(EasyContract.class).protocols()).contains("HTTP");
        assertThat(flow.getAnnotation(EasyContract.class).protocols()).contains("HTTP", "MQTT", "KAFKA", "RPC");
    }

    @Test
    void rpcAtomCanUseRegisteredInvoker() {
        IotEngineeringRuntime.bootstrap();
        ContractContext context = new ContractContext(Map.of("deviceId", "dev-1"));
        var result = new RpcDeviceAtom().queryDevice(context);
        assertThat(result.passed()).isTrue();
        assertThat(context.outputs()).containsKey("device");
    }
}
