package com.testhub.core.feature.apiscenario;

import com.testhub.common.web.ApiException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.testhub.core.feature.apiscenario.ApiScenarioDtos.GraphNode;
import static com.testhub.core.feature.apiscenario.ApiScenarioDtos.ScenarioGraph;
import static com.testhub.core.feature.apiscenario.ApiScenarioDtos.VariableFlow;

/**
 * Read-only projection of the YAML scenario: step tree + variable producer/consumer chain.
 * It deliberately does not persist anything; YAML remains the source of truth.
 */
@Component
public class ScenarioGraphAnalyzer {

    private static final Pattern TEMPLATE = Pattern.compile("\\$\\{([^}]+)}|\\{\\{([^}]+)}}");
    private static final Pattern GROOVY_PUT = Pattern.compile("vars\\.put\\(\\s*[\"']([^\"']+)[\"']");

    public ScenarioGraph analyze(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw ApiException.badRequest("YAML 不能为空");
        }
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (Exception e) {
            throw ApiException.badRequest("YAML 解析失败: " + e.getMessage());
        }
        if (!(loaded instanceof Map<?, ?> rootMap)) {
            throw ApiException.badRequest("场景 YAML 根节点必须是对象");
        }
        Map<String, Object> root = map(rootMap);
        Run run = new Run();
        run.datasets = map(root.get("datasets"));
        Map<String, Object> variables = map(root.get("variables"));
        for (String key : variables.keySet()) {
            run.define(key, "场景变量");
        }
        Map<String, Object> environment = map(root.get("environment"));
        for (String key : map(environment.get("variables")).keySet()) {
            run.define(key, "环境变量");
        }
        if (environment.get("baseUrl") != null) {
            run.define("baseUrl", "环境变量");
        }
        List<GraphNode> steps = run.steps(list(root.get("steps")));
        run.finish();
        return new ScenarioGraph(run.count, run.warnings, run.flows(), steps);
    }

    private final class Run {
        private final Map<String, String> defined = new LinkedHashMap<>();
        private final Map<String, VariableFlow> flows = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();
        private Map<String, Object> datasets = Map.of();
        private int count;
        private int sequence;

        void define(String name, String producer) {
            if (name == null || name.isBlank()) return;
            defined.putIfAbsent(name, producer);
            flows.putIfAbsent(name, new VariableFlow(name, producer, new ArrayList<>()));
        }

        List<GraphNode> steps(List<Map<String, Object>> source) {
            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> step : source) {
                nodes.addAll(step(step));
            }
            return nodes;
        }

        private List<GraphNode> step(Map<String, Object> step) {
            List<GraphNode> output = new ArrayList<>();
            output.addAll(listOfHooks(step.get("before"), "前置"));
            String type = text(step.get("type"));
            String name = text(step.get("name"));
            if (name == null || name.isBlank()) {
                name = (type == null ? "step" : type) + " #" + (sequence + 1);
            }
            String nodeId = "n" + (++sequence);
            final String nodeName = name;
            List<String> ownConsumes = templates(step, Set.of("steps", "elseSteps", "before", "after"));
            List<String> ownProduces = produces(step);
            List<String> warnings = new ArrayList<>();
            for (String variable : ownConsumes) {
                VariableFlow flow = flows.get(variable);
                if (flow != null) {
                    if (!flow.consumers().contains(name)) flow.consumers().add(name);
                } else {
                    String message = "步骤「" + name + "」引用了未定义变量 ${" + variable + "}";
                    warnings.add(message);
                    this.warnings.add(message);
                }
            }

            List<GraphNode> children = new ArrayList<>();
            if ("if".equalsIgnoreCase(type)) {
                children.addAll(steps(maps(step.get("steps"))));
                children.addAll(steps(maps(step.get("elseSteps"))));
            } else if ("loop".equalsIgnoreCase(type) || "while".equalsIgnoreCase(type)
                    || "once".equalsIgnoreCase(type)) {
                defineLoopVariables(step, name);
                children.addAll(steps(maps(step.get("steps"))));
            }
            ownProduces.forEach(variable -> define(variable, nodeName));
            count++;
            output.add(new GraphNode(nodeId, name, type, summary(type, step),
                    ownConsumes, ownProduces, List.copyOf(warnings), List.copyOf(children)));
            output.addAll(listOfHooks(step.get("after"), "后置"));
            return output;
        }

        private List<GraphNode> listOfHooks(Object value, String label) {
            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> hook : maps(value)) {
                for (GraphNode node : step(hook)) {
                    nodes.add(new GraphNode(node.id(), "[" + label + "] " + node.name(), node.type(),
                            node.summary(), node.consumes(), node.produces(), node.warnings(), node.children()));
                }
            }
            return nodes;
        }

        private void defineLoopVariables(Map<String, Object> step, String producer) {
            String indexVar = text(step.get("indexVar"));
            String itemVar = text(step.get("itemVar"));
            define(indexVar == null ? "index" : indexVar, producer);
            define(itemVar == null ? "item" : itemVar, producer);
            String dataset = text(step.get("dataset"));
            if (dataset == null) return;
            for (Map<String, Object> row : maps(datasets.get(dataset))) {
                row.keySet().forEach(key -> define(key, producer));
            }
        }

        List<VariableFlow> flows() {
            return new ArrayList<>(flows.values());
        }

        void finish() {
            if (count == 0) warnings.add("场景没有步骤");
        }
    }

    private static List<String> produces(Map<String, Object> step) {
        Set<String> produces = new LinkedHashSet<>();
        produces.addAll(map(step.get("extract")).keySet());
        produces.addAll(map(step.get("variables")).keySet());
        String type = text(step.get("type"));
        if ("loop".equalsIgnoreCase(type) || "while".equalsIgnoreCase(type)) {
            String index = text(step.get("indexVar"));
            String item = text(step.get("itemVar"));
            produces.add(index == null ? "index" : index);
            produces.add(item == null ? "item" : item);
        }
        if ("groovy".equalsIgnoreCase(type)) {
            Matcher matcher = GROOVY_PUT.matcher(text(step.get("script")));
            while (matcher.find()) produces.add(matcher.group(1));
        }
        return new ArrayList<>(produces);
    }

    private static List<String> templates(Map<String, Object> step, Set<String> skip) {
        Set<String> names = new LinkedHashSet<>();
        collectTemplates(step, skip, names);
        return new ArrayList<>(names);
    }

    private static void collectTemplates(Object value, Set<String> skip, Set<String> names) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (skip.contains(String.valueOf(entry.getKey()))) continue;
                collectTemplates(entry.getValue(), skip, names);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) collectTemplates(item, skip, names);
        } else if (value instanceof String text) {
            Matcher matcher = TEMPLATE.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
                if (name != null && !name.isBlank()) names.add(name.trim());
            }
        }
    }

    private static String summary(String type, Map<String, Object> step) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String value = switch (normalized) {
            case "http" -> first(text(step.get("method")), "GET") + " " + first(text(step.get("url")), text(step.get("path")));
            case "sql" -> text(step.get("query"));
            case "groovy" -> "自定义 Groovy 脚本";
            case "assert" -> maps(step.get("assertions")).size() + " 个断言";
            case "set" -> "设置 " + map(step.get("variables")).keySet();
            case "wait" -> "等待 " + step.getOrDefault("ms", 500) + "ms";
            case "if" -> "条件: " + text(step.get("condition"));
            case "loop" -> {
                if (text(step.get("dataset")) != null) yield "数据集: " + text(step.get("dataset"));
                if (step.get("items") != null) yield "列表: " + ((List<?>) step.get("items")).size() + " 项";
                yield "次数: " + step.getOrDefault("times", 0);
            }
            case "once" -> "只执行一次";
            case "while" -> "条件循环: " + text(step.get("condition"));
            default -> "";
        };
        if (value == null) return "";
        return value.length() > 180 ? value.substring(0, 177) + "..." : value;
    }

    private static String first(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) result.add(map(map));
        }
        return result;
    }

    private static List<Map<String, Object>> list(Object value) {
        return maps(value);
    }
}
