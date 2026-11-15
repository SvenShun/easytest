package com.easytest.iot;

import com.easytest.engineering.RpcAtom;

import java.util.List;
import java.util.Map;

/**
 * Bind enterprise RPC client at engineering startup.
 *
 * <p>Real deployments replace the lambda with a Dubbo GenericService / Feign /
 * gRPC invoker; the flow/action code stays unchanged.</p>
 */
public final class IotEngineeringRuntime {

    private IotEngineeringRuntime() {
    }

    static {
        bootstrap();
    }

    public static void bootstrap() {
        String registry = System.getenv("IOT_DUBBO_REGISTRY");
        if (registry != null && !registry.isBlank()) {
            RpcAtom.register(new IotDubboRpcInvoker(registry,
                    System.getenv().getOrDefault("IOT_DUBBO_APP", "easytest-iot"),
                    System.getenv().getOrDefault("IOT_DUBBO_GROUP", "iot")));
            return;
        }
        RpcAtom.register((service, method, args, attachments) -> Map.of(
                "service", service, "method", method, "args", args == null ? List.of() : args,
                "mock", true));
    }
}
