package com.testhub.core.feature.apiscenario.git;

import com.testhub.core.feature.apiscenario.ApiScenario;
import com.testhub.core.feature.apiscenario.ApiScenarioRepository;
import com.testhub.core.feature.apiscenario.ScenarioGraphAnalyzer;
import com.testhub.core.feature.apiscenario.git.ScenarioGitDtos.ConfigRequest;
import com.testhub.core.feature.apiscenario.git.ScenarioGitDtos.ConfigView;
import com.testhub.core.feature.apiscenario.git.ScenarioGitDtos.SyncResult;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Git is the source of truth; this service only materializes YAML files into the platform. */
@Service
public class ScenarioGitSyncService {

    private static final String CONFIG_ID = "default";
    private static final int MAX_FILES = 500;

    private final ScenarioGitConfigRepository configs;
    private final ApiScenarioRepository scenarios;
    private final ScenarioGraphAnalyzer graphAnalyzer;

    public ScenarioGitSyncService(ScenarioGitConfigRepository configs, ApiScenarioRepository scenarios,
                                  ScenarioGraphAnalyzer graphAnalyzer) {
        this.configs = configs;
        this.scenarios = scenarios;
        this.graphAnalyzer = graphAnalyzer;
    }

    public ConfigView config() {
        ScenarioGitConfig config = configs.findById(CONFIG_ID).orElse(null);
        if (config == null) {
            return new ConfigView(null, "main", ".", false, false, null, null, null, null);
        }
        return view(config);
    }

    @Transactional
    public ConfigView update(ConfigRequest request) {
        ScenarioGitConfig config = configs.findById(CONFIG_ID).orElseGet(() -> new ScenarioGitConfig(CONFIG_ID));
        String repoUrl = trim(request == null ? null : request.repoUrl());
        boolean enabled = request != null && Boolean.TRUE.equals(request.enabled());
        if (enabled && (repoUrl == null || repoUrl.isBlank())) {
            throw ApiException.badRequest("启用 Git 同步前必须填写仓库地址");
        }
        String branch = trim(request == null ? null : request.branch());
        String directory = trim(request == null ? null : request.yamlDirectory());
        config.setRepoUrl(repoUrl);
        config.setBranchName(branch == null ? "main" : branch);
        config.setYamlDirectory(directory == null ? "." : directory);
        config.setEnabled(enabled);
        if (request != null && request.webhookSecret() != null && !request.webhookSecret().isBlank()) {
            config.setWebhookSecret(request.webhookSecret().trim());
        }
        config.setUpdatedAt(Instant.now());
        return view(configs.save(config));
    }

    /** Synchronous for simple deployments and webhook callers. */
    @Transactional
    public synchronized SyncResult sync(String actor) {
        ScenarioGitConfig config = configs.findById(CONFIG_ID)
                .orElseThrow(() -> ApiException.badRequest("尚未配置 Git 仓库"));
        if (!config.isEnabled()) {
            throw ApiException.conflict("Git 同步未启用");
        }
        if (config.getRepoUrl() == null || config.getRepoUrl().isBlank()) {
            throw ApiException.badRequest("Git 仓库地址为空");
        }
        Instant now = Instant.now();
        List<String> errors = new ArrayList<>();
        try {
            Path repo = prepareRepository(config);
            String commit = git(repo, "rev-parse", "HEAD").trim();
            Path base = repo.toAbsolutePath().normalize();
            String directory = config.getYamlDirectory() == null ? "." : config.getYamlDirectory();
            Path directoryPath = base.resolve(directory).normalize();
            if (!directoryPath.startsWith(base)) {
                throw ApiException.badRequest("yamlDirectory 不能超出仓库目录");
            }
            if (!Files.isDirectory(directoryPath)) {
                throw ApiException.badRequest("YAML 目录不存在: " + directory);
            }
            List<Path> files;
            try (Stream<Path> stream = Files.walk(directoryPath)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".yaml") || name.endsWith(".yml");
                        })
                        .filter(path -> !path.toString().contains("/.git/"))
                        .sorted(Comparator.comparing(Path::toString))
                        .limit(MAX_FILES)
                        .toList();
            }
            int created = 0;
            int updated = 0;
            for (Path file : files) {
                try {
                    String yamlText = Files.readString(file, StandardCharsets.UTF_8);
                    Map<String, Object> root = root(yamlText);
                    String sourcePath = base.relativize(file).toString().replace('\\', '/');
                    String name = string(root.get("name"));
                    if (name == null || name.isBlank()) {
                        name = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
                    }
                    String description = string(root.get("description"));
                    // Reuse the same static projection/validation as the editor.
                    graphAnalyzer.analyze(yamlText);
                    ApiScenario existing = scenarios.findBySourcePath(sourcePath).orElse(null);
                    if (existing == null) {
                        ApiScenario scenario = new ApiScenario(Ids.newId("scenario"), name, description,
                                yamlText, null, true, actor == null ? "git-sync" : actor, now, now);
                        scenario.setSourcePath(sourcePath);
                        scenario.setSourceCommit(commit);
                        scenarios.save(scenario);
                        created++;
                    } else {
                        existing.setName(name);
                        existing.setDescription(description);
                        existing.setYamlText(yamlText);
                        existing.setSourcePath(sourcePath);
                        existing.setSourceCommit(commit);
                        existing.setUpdatedAt(now);
                        scenarios.save(existing);
                        updated++;
                    }
                } catch (Exception e) {
                    errors.add(file.getFileName() + ": " + e.getMessage());
                }
            }
            config.setLastCommit(commit);
            config.setLastSyncAt(now);
            config.setLastError(errors.isEmpty() ? null : String.join("; ", errors).substring(0,
                    Math.min(1900, String.join("; ", errors).length())));
            config.setUpdatedAt(now);
            configs.save(config);
            return new SyncResult(commit, files.size(), created, updated, errors, now);
        } catch (Exception e) {
            config.setLastError(e.getMessage());
            config.setUpdatedAt(now);
            configs.save(config);
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw ApiException.internal("Git 同步失败: " + e.getMessage());
        }
    }

    public boolean verifyWebhook(String signature, String token, String body) {
        ScenarioGitConfig config = configs.findById(CONFIG_ID).orElse(null);
        if (config == null || !config.isEnabled() || config.getWebhookSecret() == null
                || config.getWebhookSecret().isBlank()) {
            return false;
        }
        if (token != null && MessageDigest.isEqual(config.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        if (signature == null || body == null) {
            return false;
        }
        try {
            String expected = "sha256=" + HexFormat.of().formatHex(hmac(config.getWebhookSecret(), body));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private Path prepareRepository(ScenarioGitConfig config) throws Exception {
        String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((config.getRepoUrl() + "|" + config.getBranchName()).getBytes(StandardCharsets.UTF_8)))
                .substring(0, 16);
        Path root = Paths.get("data", "git-scenarios", key).toAbsolutePath().normalize();
        Files.createDirectories(root.getParent());
        if (!Files.exists(root.resolve(".git"))) {
            if (Files.exists(root)) {
                deleteTree(root);
            }
            run(root.getParent(), "git", "clone", "--depth", "1", "--branch", config.getBranchName(),
                    config.getRepoUrl(), root.toString());
        } else {
            run(root, "git", "fetch", "--depth", "1", "origin", config.getBranchName());
            run(root, "git", "reset", "--hard", "origin/" + config.getBranchName());
        }
        return root;
    }

    private String git(Path dir, String... args) throws IOException, InterruptedException {
        return run(dir, concat("git", args));
    }

    private String run(Path directory, String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            builder.directory(directory.toFile());
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(Duration.ofSeconds(120).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git 命令超时: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IOException("Git 命令失败: " + output.trim());
        }
        return output;
    }

    private void deleteTree(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private Map<String, Object> root(String yamlText) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        if (!(loaded instanceof Map<?, ?> map)) {
            throw ApiException.badRequest("YAML 根节点必须是对象");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private byte[] hmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    }

    private ConfigView view(ScenarioGitConfig config) {
        return new ConfigView(config.getRepoUrl(), config.getBranchName(), config.getYamlDirectory(),
                config.isEnabled(), config.getWebhookSecret() != null && !config.getWebhookSecret().isBlank(),
                config.getLastCommit(), config.getLastSyncAt(), config.getLastError(), config.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String[] concat(String first, String[] rest) {
        String[] result = new String[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }
}
