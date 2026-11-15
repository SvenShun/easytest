package com.easytest.engineering;

import java.util.List;
import java.util.Map;

/**
 * Pluggable RPC atom. Enterprises bind this to Dubbo/Feign/gRPC by supplying an
 * {@link RpcInvoker}; the test engineering project keeps one invocation contract.
 */
public final class RpcAtom {

    private static volatile RpcInvoker invoker;

    private RpcAtom() {
    }

    public interface RpcInvoker {
        Object invoke(String service, String method, List<Object> args, Map<String, Object> attachments);
    }

    public static void register(RpcInvoker customInvoker) {
        invoker = customInvoker;
    }

    public static Object invoke(String service, String method, List<Object> args,
                                Map<String, Object> attachments) {
        if (invoker == null) {
            throw new IllegalStateException("未配置 RpcInvoker（可绑定 Dubbo GenericService/Feign/gRPC）");
        }
        return invoker.invoke(service, method, args, attachments);
    }
}
