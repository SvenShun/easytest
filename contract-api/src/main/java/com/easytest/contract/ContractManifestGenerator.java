package com.easytest.contract;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Generates {@code easytest-contract.yaml} from a compiled engineering JAR/class directory.
 *
 * <pre>
 * java -cp easytest-contract-api.jar:engineering.jar \
 *   com.easytest.contract.ContractManifestGenerator engineering.jar manifest.yaml
 * </pre>
 */
public final class ContractManifestGenerator {

    private ContractManifestGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: ContractManifestGenerator <jar-or-classes-dir> [output.yaml]");
            System.exit(2);
        }
        Path source = Paths.get(args[0]).toAbsolutePath().normalize();
        String manifest = generate(source);
        if (args.length > 1) {
            Files.writeString(Paths.get(args[1]), manifest, StandardCharsets.UTF_8);
        } else {
            System.out.print(manifest);
        }
    }

    public static String generate(Path source) throws Exception {
        List<ContractDescriptor> descriptors = scan(source);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("generatedAt", Instant.now().toString());
        root.put("source", source.toString());
        List<Map<String, Object>> methods = new ArrayList<>();
        for (ContractDescriptor descriptor : descriptors) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", descriptor.id());
            item.put("name", descriptor.name());
            item.put("description", descriptor.description());
            item.put("methodPath", descriptor.methodPath());
            item.put("inputs", descriptor.inputs());
            item.put("outputs", descriptor.outputs());
            item.put("tags", descriptor.tags());
            methods.add(item);
        }
        root.put("methods", methods);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options).dump(root);
    }

    private static List<ContractDescriptor> scan(Path source) throws Exception {
        List<Path> entries = new ArrayList<>();
        boolean jar = source.toString().endsWith(".jar");
        if (jar) {
            entries.add(source);
        } else if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.walk(source)) {
                stream.filter(path -> path.toString().endsWith(".class")).forEach(entries::add);
            }
        } else {
            throw new IOException("不是 JAR 或 classes 目录: " + source);
        }
        if (entries.isEmpty()) return List.of();
        URL[] urls = jar ? new URL[]{source.toUri().toURL()} : new URL[]{source.toUri().toURL()};
        List<ContractDescriptor> result = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(urls, ContractManifestGenerator.class.getClassLoader())) {
            if (jar) {
                try (JarFile file = new JarFile(source.toFile())) {
                    file.stream().map(JarEntry::getName)
                            .filter(name -> name.endsWith(".class") && !name.contains("$"))
                            .sorted()
                            .forEach(name -> scanClass(loader, name, result));
                }
            } else {
                for (Path entry : entries) {
                    String relative = source.relativize(entry).toString().replace('\\', '/');
                    if (!relative.contains("$")) scanClass(loader, relative.replace('/', '.'), result);
                }
            }
        }
        result.sort(Comparator.comparing(ContractDescriptor::id).thenComparing(ContractDescriptor::methodPath));
        return result;
    }

    private static void scanClass(ClassLoader loader, String resource, List<ContractDescriptor> result) {
        String className = resource.endsWith(".class")
                ? resource.substring(0, resource.length() - ".class".length()).replace('/', '.')
                : resource.replace('/', '.');
        try {
            Class<?> type = Class.forName(className, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                EasyContract annotation = method.getAnnotation(EasyContract.class);
                if (annotation == null || !java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
                if (method.getParameterCount() != 1
                        || !ContractContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    continue;
                }
                String path = type.getName() + "#" + method.getName();
                String name = annotation.name().isBlank() ? method.getName() : annotation.name();
                result.add(new ContractDescriptor(annotation.id(), name, annotation.description(), path,
                        List.of(annotation.inputs()), List.of(annotation.outputs()), List.of(annotation.tags())));
            }
        } catch (Throwable ignored) {
            // Optional classes may depend on classes not present in the given jar.
        }
    }
}
