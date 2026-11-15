package com.easytest.core.feature.engineering;

import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.engineering.EngineeringMethodDtos.ManifestImportRequest;
import com.easytest.core.feature.engineering.EngineeringMethodDtos.MethodView;
import com.easytest.core.feature.engineering.EngineeringMethodDtos.SyncResult;
import com.easytest.core.feature.plan.ApiExecutionClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persisted method registry; agent/Manifest are upstream sources, platform stores a projection. */
@Service
public class EngineeringMethodService {

    private final EngineeringMethodRepository methods;
    private final ApiExecutionClient apiExecutionClient;
    private final ObjectMapper objectMapper;

    public EngineeringMethodService(EngineeringMethodRepository methods, ApiExecutionClient apiExecutionClient,
                                    ObjectMapper objectMapper) {
        this.methods = methods;
        this.apiExecutionClient = apiExecutionClient;
        this.objectMapper = objectMapper;
    }

    public List<MethodView> list() {
        return methods.findAll().stream().map(this::view).toList();
    }

    public List<MethodView> listByRepo(String repoId) {
        return methods.findByRepoId(repoId).stream().map(this::view).toList();
    }

    @Transactional
    public SyncResult syncFromAgents() {
        int imported = 0;
        List<String> errors = new ArrayList<>();
        try {
            for (ApiExecutionClient.EngineeringMethod method : apiExecutionClient.listEngineeringMethods()) {
                upsert(method.id(), method.name(), method.description(), method.methodPath(),
                        method.inputs(), method.outputs(), method.tags(), "agent",
                        method.layer(), method.protocols());
                imported++;
            }
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
        }
        return new SyncResult(imported, methods.findAll().size(), errors);
    }

    @Transactional
    public SyncResult importManifest(ManifestImportRequest request) {
        if (request == null || request.yaml() == null || request.yaml().isBlank()) {
            throw ApiException.badRequest("manifest 不能为空");
        }
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(request.yaml());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("manifest YAML 解析失败: " + e.getMessage());
        }
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("methods") instanceof List<?> list)) {
            throw ApiException.badRequest("manifest 必须包含 methods 数组");
        }
        int imported = 0;
        List<String> errors = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String methodPath = string(map.get("methodPath"));
            if (methodPath == null) {
                errors.add("跳过缺少 methodPath 的方法");
                continue;
            }
            upsert(string(map.get("id")), string(map.get("name")), string(map.get("description")),
                    methodPath, strings(map.get("inputs")), strings(map.get("outputs")),
                    strings(map.get("tags")), "manifest",
                    string(map.get("layer")), strings(map.get("protocols")));
            imported++;
        }
        return new SyncResult(imported, methods.findAll().size(), errors);
    }

    private void upsert(String contractId, String name, String description, String methodPath,
                        List<String> inputs, List<String> outputs, List<String> tags, String source) {
        upsert(contractId, name, description, methodPath, inputs, outputs, tags, source, "FLOW", List.of());
    }

    private void upsert(String contractId, String name, String description, String methodPath,
                        List<String> inputs, List<String> outputs, List<String> tags, String source,
                        String layer, List<String> protocols) {
        EngineeringMethod method = methods.findByMethodPath(methodPath)
                .orElseGet(() -> new EngineeringMethod(Ids.newId("emethod"), contractId, name, description,
                        methodPath, json(inputs), json(outputs), json(tags), source, Instant.now()));
        method.setContractId(contractId);
        method.setName(name == null || name.isBlank() ? methodPath.substring(methodPath.lastIndexOf('#') + 1) : name);
        method.setDescription(description);
        method.setMethodPath(methodPath);
        method.setInputsJson(json(inputs));
        method.setOutputsJson(json(outputs));
        method.setTagsJson(json(tags));
        method.setSource(source);
        method.setLayer(layer == null || layer.isBlank() ? "FLOW" : layer.toUpperCase(java.util.Locale.ROOT));
        method.setProtocolsJson(json(protocols));
        method.setUpdatedAt(Instant.now());
        methods.save(method);
    }

    private MethodView view(EngineeringMethod method) {
        return new MethodView(method.getId(), method.getContractId(), method.getName(), method.getDescription(),
                method.getMethodPath(), readList(method.getInputsJson()), readList(method.getOutputsJson()),
                readList(method.getTagsJson()), method.getSource(), method.getUpdatedAt(),
                method.getProjectId(), method.getRepoId(), method.getKind(), method.getPayloadJson(),
                method.getLayer(), readList(method.getProtocolsJson()));
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        list.forEach(item -> { if (item != null) result.add(String.valueOf(item)); });
        return result;
    }

    private static String string(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
