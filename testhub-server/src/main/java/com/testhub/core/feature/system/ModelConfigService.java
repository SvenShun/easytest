package com.testhub.core.feature.system;

import com.testhub.common.security.AesGcmCipher;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.SecurityProperties;
import com.testhub.common.web.ApiException;
import com.testhub.core.config.CoreProperties;
import com.testhub.core.feature.support.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Model configuration lifecycle and secret handling. */
@Service
public class ModelConfigService {

    public static final String TEXT = "text";
    public static final String VISION = "vision";

    private static final Set<String> CONFIG_TYPES = Set.of(TEXT, VISION);
    private static final Set<String> PROVIDERS = Set.of("mock", "openai", "deepseek");
    private static final Map<String, Integer> TYPE_ORDER = Map.of(TEXT, 0, VISION, 1);

    private final ModelConfigRepository repository;
    private final SecurityProperties securityProperties;
    private final CoreProperties coreProperties;
    private final AuditService auditService;

    public ModelConfigService(ModelConfigRepository repository, SecurityProperties securityProperties,
                              CoreProperties coreProperties, AuditService auditService) {
        this.repository = repository;
        this.securityProperties = securityProperties;
        this.coreProperties = coreProperties;
        this.auditService = auditService;
    }

    public List<ModelConfigView> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparingInt(config -> TYPE_ORDER.getOrDefault(config.getConfigType(), 9)))
                .map(this::view)
                .toList();
    }

    @Transactional
    public ModelConfigView update(String configType, UpdateModelConfigRequest request) {
        String type = normalizeType(configType);
        ModelConfig config = repository.findByConfigType(type)
                .orElseGet(() -> new ModelConfig("mc-" + type, type, null, "", null, null, null, null));
        if (request == null || isBlank(request.provider())) {
            throw ApiException.badRequest("provider 不能为空");
        }
        String provider = request.provider().trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(provider)) {
            throw ApiException.badRequest("不支持的 provider: " + request.provider());
        }
        String modelId = isBlank(request.modelId()) ? config.getModelId() : request.modelId().trim();
        if (isBlank(modelId)) {
            throw ApiException.badRequest("modelId 不能为空");
        }
        config.setProvider(provider);
        config.setModelId(modelId);
        if ("mock".equals(provider)) {
            // mock providers need neither endpoint nor key
            config.setEndpoint("");
        } else if (request.endpoint() != null) {
            config.setEndpoint(request.endpoint().trim());
        }
        if (!isBlank(request.apiKey()) && !"mock".equals(provider)) {
            config.setApiKeyEnc(AesGcmCipher.encrypt(request.apiKey().trim(),
                    securityProperties.getEncryptionKey()));
        }
        config.setUpdatedBy(currentActor());
        config.setUpdatedAt(Instant.now());
        ModelConfig saved = repository.save(config);
        auditService.record(AuditService.MODEL_CONFIG_UPDATE, "model-config:" + type,
                "provider=" + provider + ",modelId=" + modelId);
        return view(saved);
    }

    /** Credential for internal distribution; 409 when the model is not configured. */
    public ModelCredential requireCredential(String configType) {
        String type = normalizeType(configType);
        ModelConfig config = repository.findByConfigType(type)
                .filter(this::configured)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "MODEL_NOT_CONFIGURED",
                        type + " 模型尚未配置"));
        return new ModelCredential(type, config.getProvider(), nullToEmpty(config.getEndpoint()),
                decryptKey(config), config.getModelId(),
                Instant.now().plus(coreProperties.getCredentialTtl()));
    }

    public ModelCredential requireTextCredential() {
        return requireCredential(TEXT);
    }

    public ModelConfigView view(ModelConfig config) {
        String masked = "";
        if (!isBlank(config.getApiKeyEnc())) {
            try {
                masked = mask(AesGcmCipher.decrypt(config.getApiKeyEnc(), securityProperties.getEncryptionKey()));
            } catch (RuntimeException e) {
                masked = "****";
            }
        }
        return new ModelConfigView(config.getConfigType(), nullToEmpty(config.getProvider()),
                nullToEmpty(config.getEndpoint()), nullToEmpty(config.getModelId()), masked,
                configured(config), config.getUpdatedBy(), config.getUpdatedAt());
    }

    public boolean configured(ModelConfig config) {
        return config != null && !isBlank(config.getProvider()) && !isBlank(config.getModelId());
    }

    public static String normalizeType(String configType) {
        String type = configType == null ? "" : configType.trim().toLowerCase(Locale.ROOT);
        if (!CONFIG_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "未知的模型配置类型: " + configType);
        }
        return type;
    }

    private String decryptKey(ModelConfig config) {
        if (isBlank(config.getApiKeyEnc())) {
            return "";
        }
        String decrypted = AesGcmCipher.decrypt(config.getApiKeyEnc(), securityProperties.getEncryptionKey());
        return decrypted == null ? "" : decrypted;
    }

    private String mask(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    private String currentActor() {
        AuthPrincipal principal = CurrentUser.get();
        return principal == null ? "system" : principal.username();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
