package com.easytest.apiexec.contract;

import com.easytest.apiexec.config.ApiExecProperties;
import com.easytest.contract.ContractContext;
import com.easytest.contract.ContractDescriptor;
import com.easytest.contract.ContractResult;
import com.easytest.contract.EasyContract;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Discovers annotated engineering methods from jars in a host/agent directory.
 * Platform-side contract YAML references a method path; the agent owns the code.
 */
@Component
public class EngineeringMethodRegistry {

    private static final Logger log = LoggerFactory.getLogger(EngineeringMethodRegistry.class);

    private final ApiExecProperties properties;
    private final Map<String, Entry> methods = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "contract-method");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean loaded;

    public EngineeringMethodRegistry(ApiExecProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            list();
        } catch (RuntimeException e) {
            log.warn("engineering method scan failed: {}", e.getMessage());
        }
    }

    public synchronized List<ContractDescriptor> list() {
        if (!loaded) {
            reload();
        }
        return methods.values().stream().map(entry -> entry.descriptor).toList();
    }

    public synchronized void reload() {
        methods.clear();
        loaded = true;
        Path dir = Paths.get(properties.getEngineeringDir() == null ? "data/engineering"
                : properties.getEngineeringDir());
        if (!Files.isDirectory(dir)) {
            log.info("engineering directory not found: {}（不执行代码组件，YAML 场景仍可用）", dir);
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> jars = stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
            if (jars.isEmpty()) {
                return;
            }
            URL[] urls = jars.stream().map(this::url).toArray(URL[]::new);
            URLClassLoader loader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
            for (Path jar : jars) {
                try (JarFile file = new JarFile(jar.toFile())) {
                    file.stream().map(JarEntry::getName)
                            .filter(name -> name.endsWith(".class") && !name.contains("$"))
                            .forEach(name -> loadClass(loader, name));
                }
            }
            log.info("registered {} engineering contract methods from {}", methods.size(), dir);
        } catch (IOException e) {
            log.warn("engineering scan failed: {}", e.getMessage());
        }
    }

    /** Writes an uploaded JAR into the engineering directory and reloads methods. */
    public synchronized void installJar(String filename, byte[] content) {
        try {
            Path dir = Paths.get(properties.getEngineeringDir() == null ? "data/engineering"
                    : properties.getEngineeringDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String safe = filename == null || filename.isBlank() ? "engineering-upload.jar" : filename;
            safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!safe.endsWith(".jar")) safe += ".jar";
            Files.write(dir.resolve(safe), content);
            reload();
            log.info("engineering artifact installed: {}", safe);
        } catch (IOException e) {
            throw new IllegalStateException("安装工程 JAR 失败: " + e.getMessage(), e);
        }
    }

    /** Test/embedding hook: register a class without a jar. */
    public synchronized void registerClass(Class<?> type) {
        loaded = true;
        Object instance;
        try {
            instance = type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("cannot instantiate engineering class {}: {}", type.getName(), e.getMessage());
            return;
        }
        for (Method method : type.getDeclaredMethods()) {
            EasyContract annotation = method.getAnnotation(EasyContract.class);
            if (annotation == null) continue;
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 1
                    || !ContractContext.class.isAssignableFrom(method.getParameterTypes()[0])
                    || (method.getReturnType() != void.class
                    && !ContractResult.class.isAssignableFrom(method.getReturnType()))) {
                log.warn("ignoring invalid contract method {}", type.getName() + "#" + method.getName());
                continue;
            }
            String path = type.getName() + "#" + method.getName();
            methods.put(path, new Entry(path, descriptor(path, annotation), instance, method));
        }
    }

    public Execution execute(String methodPath, Map<String, Object> inputs, long timeoutMs) {
        list();
        Entry entry = methods.get(methodPath);
        if (entry == null) {
            return Execution.fail("工程方法未注册: " + methodPath, "METHOD_NOT_FOUND", null, methodPath);
        }
        ContractContext context = new ContractContext(inputs);
        Future<Object> future = executor.submit(() -> invoke(entry, context));
        try {
            Object returned = future.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
            ContractResult result = returned instanceof ContractResult r ? r : null;
            Map<String, Object> outputs = new LinkedHashMap<>(context.outputs());
            if (result != null) outputs.putAll(result.outputs());
            boolean passed = result == null || result.passed();
            return new Execution(passed, result == null ? "contract method completed" : result.summary(),
                    outputs, List.copyOf(context.logs()), null, entry.descriptor.id(),
                    entry.descriptor.name(), entry.descriptor.description(), entry.path);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            return Execution.fail("契约方法超时: " + methodPath, "TIMEOUT", entry.descriptor, entry.path);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return Execution.fail("契约方法执行失败: " + cause.getMessage(), "METHOD_FAILED",
                    entry.descriptor, entry.path);
        }
    }

    private Object invoke(Entry entry, ContractContext context) {
        try {
            return entry.method.invoke(entry.instance, context);
        } catch (Exception e) {
            throw new IllegalStateException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e);
        }
    }

    private void loadClass(URLClassLoader loader, String resource) {
        String className = resource.substring(0, resource.length() - ".class".length()).replace('/', '.');
        try {
            Class<?> type = Class.forName(className, false, loader);
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) return;
            registerClass(type);
        } catch (Throwable error) {
            // A jar can contain optional classes with missing dependencies.
            log.debug("skip engineering class {}: {}", className, error.toString());
        }
    }

    private ContractDescriptor descriptor(String path, EasyContract annotation) {
        String name = annotation.name().isBlank() ? path.substring(path.indexOf('#') + 1) : annotation.name();
        return new ContractDescriptor(annotation.id(), name, annotation.description(), path,
                List.of(annotation.inputs()), List.of(annotation.outputs()), List.of(annotation.tags()),
                annotation.layer(), List.of(annotation.protocols()),
                annotation.inputSchema(), annotation.outputSchema());
    }

    private URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Entry {
        private final String path;
        private final ContractDescriptor descriptor;
        private final Object instance;
        private final Method method;

        private Entry(String path, ContractDescriptor descriptor, Object instance, Method method) {
            this.path = path;
            this.descriptor = descriptor;
            this.instance = instance;
            this.method = method;
        }
    }

    public record Execution(boolean passed, String summary, Map<String, Object> outputs,
                            List<String> logs, String failureReason, String contractId,
                            String contractName, String contractDescription, String methodPath) {
        static Execution fail(String summary, String reason, ContractDescriptor descriptor, String methodPath) {
            return new Execution(false, summary, Map.of(), List.of(), reason,
                    descriptor == null ? null : descriptor.id(),
                    descriptor == null ? null : descriptor.name(),
                    descriptor == null ? null : descriptor.description(), methodPath);
        }
    }
}
