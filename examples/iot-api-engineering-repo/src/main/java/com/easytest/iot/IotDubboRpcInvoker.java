package com.easytest.iot;

import com.easytest.engineering.RpcAtom;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Reflection-based Dubbo 3 GenericService invoker. No compile-time Dubbo
 * dependency is required; add dubbo jars to the runtime classpath and set
 * IOT_DUBBO_REGISTRY / IOT_DUBBO_GROUP.
 */
public final class IotDubboRpcInvoker implements RpcAtom.RpcInvoker {

    private final String registry;
    private final String application;
    private final String group;

    public IotDubboRpcInvoker(String registry, String application, String group) {
        this.registry = registry;
        this.application = application;
        this.group = group;
    }

    @Override
    public Object invoke(String service, String method, List<Object> args, Map<String, Object> attachments) {
        try {
            Class<?> applicationConfigClass = Class.forName("org.apache.dubbo.config.ApplicationConfig");
            Class<?> registryConfigClass = Class.forName("org.apache.dubbo.config.RegistryConfig");
            Class<?> referenceConfigClass = Class.forName("org.apache.dubbo.config.ReferenceConfig");
            Object applicationConfig = applicationConfigClass.getConstructor(String.class).newInstance(application);
            Object registryConfig = registryConfigClass.getConstructor(String.class).newInstance(registry);
            Object reference = referenceConfigClass.getDeclaredConstructor().newInstance();
            referenceConfigClass.getMethod("setApplication", applicationConfigClass).invoke(reference, applicationConfig);
            referenceConfigClass.getMethod("setRegistry", registryConfigClass).invoke(reference, registryConfig);
            referenceConfigClass.getMethod("setInterface", String.class).invoke(reference, service);
            referenceConfigClass.getMethod("setGeneric", String.class).invoke(reference, "true");
            if (group != null && !group.isBlank()) {
                referenceConfigClass.getMethod("setGroup", String.class).invoke(reference, group);
            }
            referenceConfigClass.getMethod("setTimeout", int.class).invoke(reference, 10_000);
            Object generic = referenceConfigClass.getMethod("get").invoke(reference);
            Method invoke = generic.getClass().getMethod("$invoke", String.class, String[].class, Object[].class);
            Object[] values = args == null ? new Object[0] : args.toArray();
            String[] types = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                types[i] = values[i] == null ? "java.lang.Object" : values[i].getClass().getName();
            }
            return invoke.invoke(generic, method, types, values);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Dubbo runtime 不在 classpath，可改用 Feign/gRPC adapter", e);
        } catch (Exception e) {
            throw new IllegalStateException("Dubbo RPC 调用失败: " + e.getMessage(), e);
        }
    }
}
