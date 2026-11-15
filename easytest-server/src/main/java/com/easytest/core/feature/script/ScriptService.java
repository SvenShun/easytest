package com.easytest.core.feature.script;

import com.easytest.common.security.AuthPrincipal;
import com.easytest.common.security.CurrentUser;
import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.common.web.PageResponse;
import com.easytest.core.feature.support.ArtifactStore;
import com.easytest.core.feature.support.AuditService;
import com.easytest.core.feature.support.PageSupport;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Script project upload / metadata / bundle access. */
@Service
public class ScriptService {

    /** 50MB bundle limit (multipart itself allows 60MB). */
    public static final long MAX_BUNDLE_BYTES = 50L * 1024 * 1024;
    public static final String DEFAULT_ENTRY_COMMAND = "sh run.sh";

    private final ScriptProjectRepository repository;
    private final ArtifactStore artifactStore;
    private final AuditService auditService;

    public ScriptService(ScriptProjectRepository repository, ArtifactStore artifactStore,
                         AuditService auditService) {
        this.repository = repository;
        this.artifactStore = artifactStore;
        this.auditService = auditService;
    }

    @Transactional
    public ScriptView upload(MultipartFile file, String name, String scriptType, String entryCommand) {
        return upload(file, name, scriptType, entryCommand, null);
    }

    @Transactional
    public ScriptView upload(MultipartFile file, String name, String scriptType, String entryCommand,
                             String manifestYaml) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("脚本文件不能为空");
        }
        if (file.getSize() > MAX_BUNDLE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "脚本包不能超过 50MB");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "仅支持 zip 脚本包");
        }
        ScriptType type = parseType(scriptType);
        String scriptName = isBlank(name) ? originalName : name.trim();
        ScriptManifest manifest = parseManifest(manifestYaml);
        String command = isBlank(entryCommand)
                ? (manifest == null || isBlank(manifest.entryCommand()) ? DEFAULT_ENTRY_COMMAND
                : manifest.entryCommand().trim())
                : entryCommand.trim();
        AuthPrincipal principal = CurrentUser.require();
        String id = Ids.newId("script");
        String objectKey = "scripts/" + id + ".zip";
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.internal("读取脚本包失败");
        }
        artifactStore.put(objectKey, content);
        ScriptProject project = new ScriptProject(id, scriptName, type, command, objectKey, content.length,
                principal.username(), Instant.now());
        if (manifest != null) {
            project.setContractId(manifest.id());
            project.setDescription(manifest.description());
            project.setInputContractJson(json(manifest.inputs()));
            project.setOutputContractJson(json(manifest.outputs()));
        }
        ScriptProject saved = repository.save(project);
        auditService.record(AuditService.SCRIPT_UPLOAD, "script:" + id,
                "name=" + scriptName + ",sizeBytes=" + content.length);
        return ScriptView.of(saved);
    }

    public PageResponse<ScriptView> list(Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageSupport.of(repository.findAll(pageRequest), ScriptView::of);
    }

    public ScriptView get(String id) {
        return ScriptView.of(require(id));
    }

    public ScriptProject require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("脚本不存在: " + id));
    }

    public ScriptProject findOrNull(String id) {
        if (isBlank(id)) {
            return null;
        }
        return repository.findById(id).orElse(null);
    }

    public byte[] readBundle(String id) {
        ScriptProject script = require(id);
        byte[] content = artifactStore.get(script.getObjectKey());
        if (content == null) {
            throw ApiException.notFound("脚本包文件缺失: " + id);
        }
        return content;
    }

    @Transactional
    public void delete(String id) {
        ScriptProject script = require(id);
        repository.delete(script);
        artifactStore.delete(script.getObjectKey());
    }

    public static ScriptType parseType(String value) {
        if (isBlank(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCRIPT_TYPE", "脚本类型不能为空");
        }
        try {
            return ScriptType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCRIPT_TYPE", "不支持的脚本类型: " + value);
        }
    }

    private ScriptManifest parseManifest(String yaml) {
        if (isBlank(yaml)) return null;
        Map<?, ?> root;
        try {
            Object loaded = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(
                    new org.yaml.snakeyaml.LoaderOptions())).load(yaml);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw ApiException.badRequest("script manifest 必须是 YAML 对象");
            }
            root = map;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.badRequest("script manifest 解析失败: " + e.getMessage());
        }
        String id = text(root.get("id"));
        if (id == null) {
            throw ApiException.badRequest("script manifest 缺少 id");
        }
        return new ScriptManifest(id, text(root.get("description")), text(root.get("entryCommand")),
                strings(root.get("inputs")), strings(root.get("outputs")));
    }

    private String json(List<String> values) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> strings(Object value) {
        if (!(value instanceof java.util.List<?> list)) return List.of();
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private record ScriptManifest(String id, String description, String entryCommand,
                                  List<String> inputs, List<String> outputs) {
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
