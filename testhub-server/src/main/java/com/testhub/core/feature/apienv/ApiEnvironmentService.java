package com.testhub.core.feature.apienv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apienv.ApiEnvironmentDtos.AuthConfig;
import com.testhub.core.feature.apienv.ApiEnvironmentDtos.EnvironmentRequest;
import com.testhub.core.feature.apienv.ApiEnvironmentDtos.EnvironmentView;
import com.testhub.core.feature.plan.TestPlanRepository;
import com.testhub.core.feature.support.PageSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** CRUD for API environments (variables + auth presets). */
@Service
public class ApiEnvironmentService {

    private static final Set<String> AUTH_TYPES = Set.of("NONE", "BEARER", "BASIC", "API_KEY", "LOGIN");

    private final ApiEnvironmentRepository repository;
    private final TestPlanRepository planRepository;
    private final ObjectMapper objectMapper;

    public ApiEnvironmentService(ApiEnvironmentRepository repository, TestPlanRepository planRepository,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.objectMapper = objectMapper;
    }

    public PageResponse<EnvironmentView> list(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ApiEnvironment> result = repository.findAll(keywordSpec(keyword), pageRequest);
        return PageSupport.of(result, this::view);
    }

    public ApiEnvironment require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("环境不存在: " + id));
    }

    public EnvironmentView get(String id) {
        return view(require(id));
    }

    @Transactional
    public EnvironmentView create(EnvironmentRequest request) {
        AuthPrincipal principal = CurrentUser.require();
        validate(request);
        Instant now = Instant.now();
        AuthConfig auth = normalizeAuth(request.auth());
        ApiEnvironment env = new ApiEnvironment(Ids.newId("env"), request.name().trim(), request.baseUrl().trim(),
                json(request.headers()), json(request.variables()), auth.type(), json(auth),
                request.enabled() == null || request.enabled(), principal.username(), now, now);
        return view(repository.save(env));
    }

    @Transactional
    public EnvironmentView update(String id, EnvironmentRequest request) {
        validate(request);
        ApiEnvironment env = require(id);
        AuthConfig auth = normalizeAuth(request.auth());
        env.setName(request.name().trim());
        env.setBaseUrl(request.baseUrl().trim());
        env.setHeadersJson(json(request.headers()));
        env.setVariablesJson(json(request.variables()));
        env.setAuthType(auth.type());
        env.setAuthJson(json(auth));
        env.setEnabled(request.enabled() == null || request.enabled());
        env.setUpdatedAt(Instant.now());
        return view(repository.save(env));
    }

    @Transactional
    public void delete(String id) {
        ApiEnvironment env = require(id);
        if (planRepository.existsByEnvironmentId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "ENV_IN_USE", "环境已被测试计划引用，先解除引用后再删除");
        }
        repository.delete(env);
    }

    public EnvironmentView view(ApiEnvironment env) {
        return new EnvironmentView(env.getId(), env.getName(), env.getBaseUrl(),
                readMap(env.getHeadersJson()), readMap(env.getVariablesJson()),
                readAuth(env.getAuthJson(), env.getAuthType()), env.isEnabled(),
                env.getCreatedBy(), env.getCreatedAt(), env.getUpdatedAt());
    }

    private void validate(EnvironmentRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("环境名称不能为空");
        }
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw ApiException.badRequest("Base URL 不能为空");
        }
        String baseUrl = request.baseUrl().trim();
        if (!baseUrl.contains("${") && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw ApiException.badRequest("Base URL 需以 http:// 或 https:// 开头");
        }
        AuthConfig auth = normalizeAuth(request.auth());
        if ("LOGIN".equals(auth.type())) {
            if (isBlank(auth.loginPath())) {
                throw ApiException.badRequest("登录鉴权需要 loginPath");
            }
            if (isBlank(auth.tokenPath())) {
                throw ApiException.badRequest("登录鉴权需要 tokenPath（例如 $.data.token）");
            }
        }
    }

    private AuthConfig normalizeAuth(AuthConfig auth) {
        if (auth == null) {
            return new AuthConfig("NONE", null, null, null, null, null, null,
                    null, null, Map.of(), null, null, null, null);
        }
        String type = auth.type() == null ? "NONE" : auth.type().trim().toUpperCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(type)) {
            throw ApiException.badRequest("鉴权类型仅支持 NONE/BEARER/BASIC/API_KEY/LOGIN");
        }
        return new AuthConfig(type, auth.token(), auth.username(), auth.password(),
                auth.headerName(), auth.headerValue(), auth.prefix(),
                auth.loginMethod(), auth.loginPath(), auth.loginHeaders() == null ? Map.of() : auth.loginHeaders(),
                auth.loginBody(), auth.tokenPath(), auth.tokenHeader(), auth.tokenPrefix());
    }

    private Specification<ApiEnvironment> keywordSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            predicates.add(cb.like(cb.lower(root.get("baseUrl")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw ApiException.badRequest("JSON 序列化失败: " + e.getMessage());
        }
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private AuthConfig readAuth(String json, String type) {
        if (json == null || json.isBlank()) {
            return new AuthConfig(type == null ? "NONE" : type, null, null, null, null, null, null,
                    null, null, Map.of(), null, null, null, null);
        }
        try {
            return objectMapper.readValue(json, AuthConfig.class);
        } catch (Exception e) {
            return new AuthConfig(type == null ? "NONE" : type, null, null, null, null, null, null,
                    null, null, Map.of(), null, null, null, null);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
