package com.testhub.apiexec.scenario;

import com.testhub.common.web.ApiException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses and validates the YAML scenario contract. */
@Component
public class ScenarioYamlParser {

    public ScenarioDefinition parse(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            throw ApiException.badRequest("场景 YAML 不能为空");
        }
        Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(50);
            loaded = new Yaml(new SafeConstructor(options)).load(yamlText);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("YAML 解析失败: " + e.getMessage());
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            throw ApiException.badRequest("场景 YAML 根节点必须是对象");
        }
        Map<String, Object> map = stringKeyMap(root);
        Object rawSteps = map.get("steps");
        if (!(rawSteps instanceof List<?> list) || list.isEmpty()) {
            throw ApiException.badRequest("场景 YAML 必须包含非空 steps 数组");
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        int index = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> stepMap)) {
                throw ApiException.badRequest("steps[" + index + "] 必须是对象");
            }
            Map<String, Object> step = stringKeyMap(stepMap);
            String type = string(step.get("type"));
            if (type == null) {
                throw ApiException.badRequest("steps[" + index + "].type 不能为空");
            }
            switch (type.toLowerCase()) {
                case "http", "sql", "groovy", "set", "assert", "wait",
                        "if", "loop", "once", "while" -> {
                }
                default -> throw ApiException.badRequest("不支持的步骤类型: " + type);
            }
            if ("if".equalsIgnoreCase(type) || "loop".equalsIgnoreCase(type)) {
                validateChildren(step, index);
            }
            steps.add(step);
            index++;
        }
        return new ScenarioDefinition(string(map.get("name")), integer(map.get("timeoutSec")),
                bool(map.get("continueOnFailure")), mapValue(map.get("variables")),
                mapValue(map.get("environment")), mapValue(map.get("datasets")), steps);
    }

    private void validateChildren(Map<String, Object> step, int index) {
        for (String key : List.of("steps", "elseSteps", "before", "after")) {
            Object value = step.get(key);
            if (value == null) {
                continue;
            }
            if (!(value instanceof List<?> children)) {
                throw ApiException.badRequest("steps[" + index + "]." + key + " 必须是数组");
            }
            for (Object child : children) {
                if (!(child instanceof Map<?, ?> map)) {
                    throw ApiException.badRequest("steps[" + index + "]." + key + " 子步骤必须是对象");
                }
                Map<String, Object> childMap = stringKeyMap(map);
                String childType = string(childMap.get("type"));
                if (childType == null) {
                    throw ApiException.badRequest("steps[" + index + "]." + key + " 子步骤 type 不能为空");
                }
                if (!java.util.Set.of("http", "sql", "groovy", "set", "assert", "wait",
                                "if", "loop", "once", "while").contains(childType.toLowerCase())) {
                    throw ApiException.badRequest("不支持的子步骤类型: " + childType);
                }
            }
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), normalize(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), normalize(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalize).toList();
        }
        return value;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), normalize(item)));
        return result;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean b ? b : value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }
}
