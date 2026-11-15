package com.easytest.core.feature.engineeringgit;

import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.engineering.EngineeringMethod;
import com.easytest.core.feature.engineering.EngineeringMethodRepository;
import com.easytest.core.feature.casemgmt.CaseSource;
import com.easytest.core.feature.casemgmt.CaseStep;
import com.easytest.core.feature.casemgmt.ClientCase;
import com.easytest.core.feature.casemgmt.ClientCaseRepository;
import com.easytest.core.feature.casemgmt.ExecutionMode;
import com.easytest.core.feature.casemgmt.ReviewStatus;
import com.easytest.core.feature.casemgmt.SelectorType;
import com.easytest.core.feature.casemgmt.StepType;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Clones an engineering repository and materializes its conventional manifest methods. */
@Component
public class EngineeringRepoScanner {

    private static final Logger log = LoggerFactory.getLogger(EngineeringRepoScanner.class);
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(120);
    private static final List<String> API_MANIFESTS = List.of(
            "easytest.api.yaml", "easytest.api.yml", "easytest-contract.yaml", "easytest-contract.yml");
    private static final List<String> UI_MANIFESTS = List.of(
            "easytest.ui.yaml", "easytest.ui.yml", "easytest-ui-contract.yaml", "easytest-ui-contract.yml");

    private final EngineeringMethodRepository methods;
    private final ObjectMapper objectMapper;
    private final ClientCaseRepository cases;

    public EngineeringRepoScanner(EngineeringMethodRepository methods, ObjectMapper objectMapper,
                                  ClientCaseRepository cases) {
        this.methods = methods;
        this.objectMapper = objectMapper;
        this.cases = cases;
    }

    @Transactional
    public ScanResult scan(EngineeringRepo repo) {
        Instant now = Instant.now();
        List<String> errors = new ArrayList<>();
        try {
            Path root = prepareRepository(repo);
            String commit = run(root, "git", "rev-parse", "HEAD").trim();
            Path base = root.toAbsolutePath().normalize();
            Path dir = base.resolve(repo.getRootDirectory() == null ? "." : repo.getRootDirectory()).normalize();
            if (!dir.startsWith(base)) throw ApiException.badRequest("rootDirectory 不能超出仓库目录");
            if (!Files.isDirectory(dir)) throw ApiException.badRequest("仓库目录不存在: " + repo.getRootDirectory());
            Path manifest = findManifest(dir, "API".equalsIgnoreCase(repo.getRepoType()) ? API_MANIFESTS : UI_MANIFESTS);
            if (manifest == null) {
                throw ApiException.badRequest("未找到约定 manifest（API: easytest.api.yaml，UI: easytest.ui.yaml）");
            }
            Map<String, Object> rootNode = root(Files.readString(manifest, StandardCharsets.UTF_8));
            List<Map<String, Object>> entries = maps(rootNode.get("API".equalsIgnoreCase(repo.getRepoType())
                    ? "methods" : "cases"));
            if (entries.isEmpty() && rootNode.get("methods") instanceof List<?> fallback) {
                entries = maps(fallback);
            }
            Set<String> seen = new LinkedHashSet<>();
            int created = 0;
            int updated = 0;
            for (Map<String, Object> entry : entries) {
                try {
                    String methodPath = methodPath(repo, entry);
                    if (methodPath == null) {
                        errors.add("跳过缺少 id/methodPath 的方法");
                        continue;
                    }
                    seen.add(methodPath);
                    EngineeringMethod existing = methods.findByMethodPath(methodPath).orElse(null);
                    boolean isNew = existing == null;
                    EngineeringMethod method = isNew
                            ? new EngineeringMethod(Ids.newId("emethod"), text(entry.get("id")),
                            text(entry.get("name")), text(entry.get("description")), methodPath,
                            json(strings(entry.get("inputs"))), json(strings(entry.get("outputs"))),
                            json(strings(entry.get("tags"))), "git:" + repo.getId(), now)
                            : existing;
                    method.setProjectId(repo.getProjectId());
                    method.setRepoId(repo.getId());
                    method.setKind(repo.getRepoType().toUpperCase(Locale.ROOT));
                    method.setContractId(first(text(entry.get("id")), method.getContractId()));
                    method.setName(first(text(entry.get("name")), methodPath.substring(methodPath.lastIndexOf('#') + 1)));
                    method.setDescription(text(entry.get("description")));
                    method.setMethodPath(methodPath);
                    method.setInputsJson(json(strings(entry.get("inputs"))));
                    method.setOutputsJson(json(strings(entry.get("outputs"))));
                    method.setTagsJson(json(strings(entry.get("tags"))));
                    method.setSource("git:" + repo.getId());
                    String caseId = "UI".equalsIgnoreCase(repo.getRepoType())
                            ? syncUiCase(repo, entry, methodPath) : null;
                    method.setLayer(first(text(entry.get("layer")), "FLOW").toUpperCase(Locale.ROOT));
                    method.setProtocolsJson(json(strings(entry.get("protocols"))));
                    method.setPayloadJson(payload(repo, entry, base, caseId));
                    method.setUpdatedAt(now);
                    methods.save(method);
                    if (isNew) created++; else updated++;
                } catch (RuntimeException e) {
                    errors.add(e.getMessage());
                }
            }
            int removed = 0;
            for (EngineeringMethod stale : methods.findByRepoId(repo.getId())) {
                if (!seen.contains(stale.getMethodPath())) {
                    methods.delete(stale);
                    removed++;
                }
            }
            repo.setLastCommit(commit);
            repo.setLastScanAt(now);
            repo.setLastError(errors.isEmpty() ? null : truncate(String.join("; ", errors)));
            repo.setUpdatedAt(now);
            return new ScanResult(commit, entries.size(), created, updated, removed, errors, now);
        } catch (Exception e) {
            repo.setLastError(truncate(e.getMessage()));
            repo.setUpdatedAt(now);
            if (e instanceof ApiException apiException) throw apiException;
            throw ApiException.internal("仓库扫描失败: " + e.getMessage());
        }
    }

    public Path workspace(EngineeringRepo repo) {
        try {
            return prepareRepository(repo);
        } catch (Exception e) {
            throw new IllegalStateException("准备仓库目录失败: " + e.getMessage(), e);
        }
    }

    public String remoteHead(EngineeringRepo repo) {
        try {
            String output = run(null, "git", "ls-remote", repo.getRepoUrl(), "refs/heads/" + repo.getBranchName());
            String first = output.lines().findFirst().orElse("");
            int tab = first.indexOf('\t');
            return tab > 0 ? first.substring(0, tab).trim() : null;
        } catch (RuntimeException e) {
            log.debug("git ls-remote failed repo={}: {}", repo.getRepoUrl(), e.getMessage());
            return null;
        }
    }

    private Path prepareRepository(EngineeringRepo repo) throws Exception {
        String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((repo.getId() + "|" + repo.getRepoUrl() + "|" + repo.getBranchName())
                        .getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        Path root = Paths.get("data", "engineering-repos", key).toAbsolutePath().normalize();
        Files.createDirectories(root.getParent());
        if (!Files.exists(root.resolve(".git"))) {
            if (Files.exists(root)) deleteTree(root);
            run(root.getParent(), "git", "clone", "--depth", "1", "--branch", repo.getBranchName(),
                    repo.getRepoUrl(), root.toString());
        } else {
            run(root, "git", "fetch", "--depth", "1", "origin", repo.getBranchName());
            run(root, "git", "reset", "--hard", "origin/" + repo.getBranchName());
        }
        return root;
    }

    private Path findManifest(Path dir, List<String> names) throws IOException {
        for (String name : names) {
            Path path = dir.resolve(name);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    private String methodPath(EngineeringRepo repo, Map<String, Object> entry) {
        String path = text(entry.get("methodPath"));
        if (path != null) return path;
        String id = text(entry.get("id"));
        return id == null ? null : ("UI".equalsIgnoreCase(repo.getRepoType()) ? "ui:" + repo.getId() + ":" : "") + id;
    }

    private String syncUiCase(EngineeringRepo repo, Map<String, Object> entry, String methodPath) {
        String caseId = "uigit-" + sha(methodPath).substring(0, 16);
        Instant now = Instant.now();
        ClientCase clientCase = cases.findById(caseId).orElseGet(() -> {
            ClientCase created = new ClientCase();
            created.setId(caseId);
            created.setCreatedAt(now);
            return created;
        });
        clientCase.setProjectId(repo.getProjectId());
        clientCase.setTitle(first(text(entry.get("name")), methodPath));
        clientCase.setBddGiven("由 Git UI 工程加载");
        clientCase.setBddWhen(first(text(entry.get("description")), "执行工程方法步骤"));
        clientCase.setBddThen("步骤全部通过");
        clientCase.setExecutionMode(ExecutionMode.STEPS);
        clientCase.setReviewStatus(ReviewStatus.APPROVED);
        clientCase.setSource(CaseSource.MANUAL);
        clientCase.setCreatedBy("git-scan");
        clientCase.setUpdatedAt(now);
        clientCase.getSteps().clear();
        int seq = 0;
        for (Map<String, Object> step : maps(entry.get("steps"))) {
            seq++;
            CaseStep entity = new CaseStep(Ids.newId("case-step"), clientCase,
                    step.get("seq") instanceof Number n ? Math.max(1, n.intValue()) : seq,
                    StepType.parse(text(step.get("stepType"))),
                    SelectorType.parse(text(step.get("selectorType"))),
                    text(step.get("selector")), text(step.get("value")), text(step.get("assertValue")),
                    step.get("timeoutMs") instanceof Number t ? t.intValue() : 0,
                    !(step.get("enabled") instanceof Boolean b) || b);
            clientCase.getSteps().add(entity);
        }
        cases.save(clientCase);
        return caseId;
    }

    private String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String payload(EngineeringRepo repo, Map<String, Object> entry, Path base, String caseId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repoId", repo.getId());
        payload.put("repoType", repo.getRepoType());
        payload.put("manifestEntry", entry);
        if (caseId != null) payload.put("caseId", caseId);
        for (String key : List.of("steps", "app", "packageName", "artifact", "scriptPath", "entryCommand")) {
            if (entry.get(key) != null) payload.put(key, entry.get(key));
        }
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> root(String yamlText) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        if (!(loaded instanceof Map<?, ?> map)) throw ApiException.badRequest("manifest 根节点必须是对象");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, itemValue) -> converted.put(String.valueOf(key), itemValue));
                result.add(converted);
            }
        }
        return result;
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private String first(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String truncate(String value) {
        return value == null || value.length() <= 1900 ? value : value.substring(0, 1900);
    }

    private String run(Path directory, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (directory != null) builder.directory(directory.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(GIT_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Git 命令超时");
            }
            if (process.exitValue() != 0) throw new IllegalStateException("Git 命令失败: " + output.trim());
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Git 命令失败: " + e.getMessage(), e);
        }
    }

    private void deleteTree(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }
}
