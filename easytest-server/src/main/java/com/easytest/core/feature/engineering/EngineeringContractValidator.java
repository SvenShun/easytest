package com.easytest.core.feature.engineering;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates component method references in a scenario contract before save/run. */
@Component
public class EngineeringContractValidator {

    private final EngineeringMethodRepository methods;

    public EngineeringContractValidator(EngineeringMethodRepository methods) {
        this.methods = methods;
    }

    public List<String> validate(String yaml) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> root = load(yaml);
        if (root == null) return errors;
        List<Map<String, Object>> steps = maps(root.get("steps"));
        walk(steps, errors);
        return errors;
    }

    private void walk(List<Map<String, Object>> steps, List<String> errors) {
        for (Map<String, Object> step : steps) {
            String type = text(step.get("type"));
            if ("component".equalsIgnoreCase(type)) {
                String methodPath = first(text(step.get("methodPath")), text(step.get("method")));
                if (methodPath == null || methodPath.isBlank()) {
                    errors.add("工程组件缺少 methodPath");
                } else {
                    EngineeringMethod method = methods.findByMethodPath(methodPath).orElse(null);
                    if (method == null) {
                        errors.add("工程方法未注册: " + methodPath + "（先在执行节点同步工程方法）");
                    } else {
                        Set<String> provided = new LinkedHashSet<>(strings(step.get("inputs")).keySet());
                        for (String input : readList(method.getInputsJson())) {
                            if (!provided.contains(input)) {
                                errors.add("工程组件 " + methodPath + " 缺少输入变量: " + input);
                            }
                        }
                    }
                }
            }
            walk(maps(step.get("steps")), errors);
            walk(maps(step.get("elseSteps")), errors);
            walk(maps(step.get("before")), errors);
            walk(maps(step.get("after")), errors);
        }
    }

    private Map<String, Object> strings(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> load(String yaml) {
        if (yaml == null || yaml.isBlank()) return null;
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
            return loaded instanceof Map<?, ?> map ? strings(map) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) result.add(strings(map));
        }
        return result;
    }

    private static String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
