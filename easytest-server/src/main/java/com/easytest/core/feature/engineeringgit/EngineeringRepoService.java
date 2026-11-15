package com.easytest.core.feature.engineeringgit;

import com.easytest.common.security.CurrentUser;
import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.engineering.EngineeringMethodRepository;
import com.easytest.core.feature.engineering.EngineeringMethod;
import com.easytest.core.feature.plan.UiExecutionClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RepoRequest;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RepoView;
import com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.ScanResult;
import com.easytest.core.feature.project.ProjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class EngineeringRepoService {

    private static final Logger log = LoggerFactory.getLogger(EngineeringRepoService.class);
    private final EngineeringRepoRepository repos;
    private final EngineeringMethodRepository methods;
    private final EngineeringRepoScanner scanner;
    private final com.easytest.core.feature.engineering.EngineeringMethodService methodService;
    private final ProjectContext projectContext;
    private final UiExecutionClient uiExecutionClient;
    private final ObjectMapper objectMapper;
    private final com.easytest.core.feature.plan.ApiExecutionClient apiExecutionClient;

    public EngineeringRepoService(EngineeringRepoRepository repos, EngineeringMethodRepository methods,
                                  EngineeringRepoScanner scanner,
                                  com.easytest.core.feature.engineering.EngineeringMethodService methodService,
                                  ProjectContext projectContext, UiExecutionClient uiExecutionClient,
                                  ObjectMapper objectMapper,
                                  com.easytest.core.feature.plan.ApiExecutionClient apiExecutionClient) {
        this.repos = repos;
        this.methods = methods;
        this.scanner = scanner;
        this.methodService = methodService;
        this.projectContext = projectContext;
        this.uiExecutionClient = uiExecutionClient;
        this.objectMapper = objectMapper;
        this.apiExecutionClient = apiExecutionClient;
    }

    public List<RepoView> list() {
        return repos.findByProjectIdOrderByUpdatedAtDesc(projectContext.currentProjectId()).stream()
                .map(this::view).toList();
    }

    @Transactional
    public RepoView create(RepoRequest request) {
        validate(request);
        Instant now = Instant.now();
        EngineeringRepo repo = new EngineeringRepo(Ids.newId("erepo"), projectContext.currentProjectId(),
                request.name().trim(), normalizeType(request.repoType()), request.repoUrl().trim(),
                blank(request.branchName()) ? "main" : request.branchName().trim(),
                blank(request.rootDirectory()) ? "." : request.rootDirectory().trim(),
                CurrentUser.require().username(), now);
        repo.setWebhookSecret(blank(request.webhookSecret()) ? null : request.webhookSecret().trim());
        repo.setEnabled(request.enabled() == null || request.enabled());
        return view(repos.save(repo));
    }

    @Transactional
    public RepoView update(String id, RepoRequest request) {
        validate(request);
        EngineeringRepo repo = require(id);
        repo.setName(request.name().trim());
        repo.setRepoType(normalizeType(request.repoType()));
        repo.setRepoUrl(request.repoUrl().trim());
        repo.setBranchName(blank(request.branchName()) ? "main" : request.branchName().trim());
        repo.setRootDirectory(blank(request.rootDirectory()) ? "." : request.rootDirectory().trim());
        repo.setEnabled(request.enabled() == null || request.enabled());
        if (!blank(request.webhookSecret())) repo.setWebhookSecret(request.webhookSecret().trim());
        repo.setUpdatedAt(Instant.now());
        return view(repos.save(repo));
    }

    @Transactional
    public void delete(String id) {
        EngineeringRepo repo = require(id);
        for (var method : methods.findByRepoId(id)) methods.delete(method);
        repos.delete(repo);
    }

    @Transactional
    public List<com.easytest.core.feature.engineering.EngineeringMethodDtos.MethodView> methods(String repoId) {
        require(repoId);
        return methodService.listByRepo(repoId);
    }

    public com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RunMethodResult runMethod(
            String repoId, String methodId,
            com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RunMethodRequest request) {
        EngineeringRepo repo = require(repoId);
        EngineeringMethod method = methods.findById(methodId)
                .filter(item -> repoId.equals(item.getRepoId()))
                .orElseThrow(() -> ApiException.notFound("工程方法不存在"));
        if (!"UI".equalsIgnoreCase(method.getKind())) {
            throw ApiException.badRequest("API 工程方法请通过 API 场景的 component 步骤执行");
        }
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> payload = method.getPayloadJson() == null ? new java.util.LinkedHashMap<>()
                    : objectMapper.readValue(method.getPayloadJson(), java.util.Map.class);
            String caseId = payload.get("caseId") == null ? null : String.valueOf(payload.get("caseId"));
            if (caseId == null) throw ApiException.badRequest("UI 方法没有关联生成用例，请重新扫描仓库");
            @SuppressWarnings("unchecked")
            java.util.List<Object> steps = payload.get("steps") instanceof java.util.List<?> list
                    ? (java.util.List<Object>) list : java.util.List.of();
            java.util.Map<String, Object> selector = new java.util.LinkedHashMap<>();
            selector.put("deviceId", request == null ? null : request.deviceId());
            selector.put("tags", java.util.List.of());
            java.util.Map<String, Object> task = new java.util.LinkedHashMap<>();
            task.put("planRunId", null);
            task.put("caseId", caseId);
            task.put("caseTitle", method.getName());
            task.put("projectId", repo.getProjectId());
            task.put("executorType", "STEP");
            task.put("targetDeviceSelector", selector);
            task.put("timeoutSec", request == null || request.timeoutSec() == null ? 120 : request.timeoutSec());
            task.put("stepTimeoutSec", request == null || request.stepTimeoutSec() == null
                    ? 30 : request.stepTimeoutSec());
            task.put("createdBy", CurrentUser.require().username());
            task.put("steps", steps);
            UiExecutionClient.TaskCreated created = uiExecutionClient.createTask(task);
            return new com.easytest.core.feature.engineeringgit.EngineeringRepoDtos.RunMethodResult(
                    created.taskId(), created.status(), created.deviceId());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.internal("下发 UI 工程方法失败: " + e.getMessage());
        }
    }

    public ScanResult scan(String id) {
        EngineeringRepo repo = require(id);
        ScanResult result;
        try {
            result = scanner.scan(repo);
        } catch (RuntimeException e) {
            repo.setLastError(e.getMessage());
            repo.setUpdatedAt(Instant.now());
            repos.save(repo);
            throw e;
        }
        repos.save(repo);
        deployApiArtifacts(repo);
        return result;
    }

    private void deployApiArtifacts(EngineeringRepo repo) {
        if (!"API".equalsIgnoreCase(repo.getRepoType())) return;
        try {
            var workspace = scanner.workspace(repo);
            for (EngineeringMethod method : methods.findByRepoId(repo.getId())) {
                if (method.getPayloadJson() == null) continue;
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> payload = objectMapper.readValue(method.getPayloadJson(), java.util.Map.class);
                Object artifact = payload.get("artifact");
                if (artifact == null) continue;
                java.nio.file.Path path = workspace.resolve(String.valueOf(artifact)).normalize();
                if (!path.startsWith(workspace) || !java.nio.file.Files.isRegularFile(path)) continue;
                apiExecutionClient.uploadEngineeringArtifact(repo.getName() + "-" + repo.getId() + ".jar",
                        java.nio.file.Files.readAllBytes(path));
                return;
            }
        } catch (Exception e) {
            log.warn("engineering artifact deploy skipped repo={}: {}", repo.getName(), e.getMessage());
        }
    }

    public boolean verifyWebhook(String repoId, String signature, String token, String body) {
        EngineeringRepo repo = repos.findById(repoId).orElse(null);
        if (repo == null || repo.getWebhookSecret() == null || repo.getWebhookSecret().isBlank()) return false;
        if (token != null && MessageDigest.isEqual(repo.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) return true;
        if (signature == null || body == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(repo.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    /** Periodic commit poll: only scans when the remote branch head changed. */
    @Scheduled(fixedDelayString = "${easytest.engineering-repo-poll-ms:120000}", initialDelay = 30000)
    public void pollRepositories() {
        for (EngineeringRepo repo : repos.findByEnabledTrue()) {
            try {
                String head = scanner.remoteHead(repo);
                if (head != null && !head.equals(repo.getLastCommit())) {
                    scan(repo.getId());
                    log.info("engineering repo rescan triggered by commit change repo={} commit={}",
                            repo.getName(), head);
                }
            } catch (RuntimeException e) {
                log.warn("engineering repo poll failed repo={}: {}", repo.getName(), e.getMessage());
            }
        }
    }

    private EngineeringRepo require(String id) {
        EngineeringRepo repo = repos.findById(id).orElseThrow(() -> ApiException.notFound("Git 工程不存在: " + id));
        if (!projectContext.currentProjectId().equals(repo.getProjectId())) {
            throw ApiException.notFound("Git 工程不存在: " + id);
        }
        return repo;
    }

    private void validate(RepoRequest request) {
        if (request == null || blank(request.name())) throw ApiException.badRequest("工程名称不能为空");
        if (blank(request.repoUrl())) throw ApiException.badRequest("Git 仓库地址不能为空");
        if (!request.repoUrl().trim().startsWith("http") && !request.repoUrl().trim().startsWith("git@")
                && !request.repoUrl().trim().startsWith("file://")) {
            throw ApiException.badRequest("Git 仓库地址需为 http(s)/git@/file:// 地址");
        }
        normalizeType(request.repoType());
    }

    private String normalizeType(String value) {
        if (blank(value)) throw ApiException.badRequest("仓库类型不能为空");
        String type = value.trim().toUpperCase(Locale.ROOT);
        if (!type.equals("API") && !type.equals("UI")) throw ApiException.badRequest("仓库类型仅支持 API/UI");
        return type;
    }

    private RepoView view(EngineeringRepo repo) {
        return new RepoView(repo.getId(), repo.getProjectId(), repo.getName(), repo.getRepoType(),
                repo.getRepoUrl(), repo.getBranchName(), repo.getRootDirectory(), repo.isEnabled(),
                repo.getLastCommit(), repo.getLastScanAt(), repo.getLastError(), repo.getCreatedBy(),
                repo.getCreatedAt(), repo.getUpdatedAt(), (int) methods.countByRepoId(repo.getId()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
