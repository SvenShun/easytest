package com.testhub.core.feature.apicase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.common.web.PageResponse;
import com.testhub.core.feature.apicase.ApiCaseDtos.ApiCaseRequest;
import com.testhub.core.feature.apicase.ApiCaseDtos.ApiCaseView;
import com.testhub.core.feature.plan.PlanCaseRepository;
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

/** CRUD for API test cases (request + assertions). */
@Service
public class ApiCaseService {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final ApiCaseRepository repository;
    private final PlanCaseRepository planCaseRepository;
    private final ObjectMapper objectMapper;

    public ApiCaseService(ApiCaseRepository repository, PlanCaseRepository planCaseRepository,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.planCaseRepository = planCaseRepository;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ApiCaseView> list(String keyword, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(PageSupport.pageIndex(page), PageSupport.pageSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ApiCase> result = repository.findAll(keywordSpec(keyword), pageRequest);
        return PageSupport.of(result, this::view);
    }

    public ApiCase require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("接口用例不存在: " + id));
    }

    public ApiCaseView get(String id) {
        return view(require(id));
    }

    @Transactional
    public ApiCaseView create(ApiCaseRequest request) {
        AuthPrincipal principal = CurrentUser.require();
        Instant now = Instant.now();
        ApiCase apiCase = new ApiCase(Ids.newId("ac"), request.name().trim(), normalizeMethod(request.method()),
                blankToNull(request.baseUrl()), request.path().trim(), json(request.headers()), json(request.query()),
                blankToNull(request.body()), json(request.assertions()), jsonDataset(request.dataset()),
                timeout(request.timeoutSec()), request.enabled() == null || request.enabled(),
                principal.username(), now, now);
        return view(repository.save(apiCase));
    }

    @Transactional
    public ApiCaseView update(String id, ApiCaseRequest request) {
        ApiCase apiCase = require(id);
        apiCase.setName(request.name().trim());
        apiCase.setMethod(normalizeMethod(request.method()));
        apiCase.setBaseUrl(blankToNull(request.baseUrl()));
        apiCase.setPath(request.path().trim());
        apiCase.setHeadersJson(json(request.headers()));
        apiCase.setQueryJson(json(request.query()));
        apiCase.setBody(blankToNull(request.body()));
        apiCase.setAssertionsJson(json(request.assertions()));
        apiCase.setDatasetJson(jsonDataset(request.dataset()));
        apiCase.setTimeoutSec(timeout(request.timeoutSec()));
        apiCase.setEnabled(request.enabled() == null || request.enabled());
        apiCase.setUpdatedAt(Instant.now());
        return view(repository.save(apiCase));
    }

    @Transactional
    public void delete(String id) {
        ApiCase apiCase = require(id);
        if (planCaseRepository.existsByCaseId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CASE_MOUNTED", "接口用例已挂载到测试计划，先移除后再删除");
        }
        repository.delete(apiCase);
    }

    public List<Map<String, Object>> dataset(ApiCase apiCase) {
        return readDataset(apiCase.getDatasetJson());
    }

    public ApiCaseView view(ApiCase apiCase) {
        return new ApiCaseView(apiCase.getId(), apiCase.getName(), apiCase.getMethod(), apiCase.getBaseUrl(),
                apiCase.getPath(), readMap(apiCase.getHeadersJson()), readMap(apiCase.getQueryJson()),
                apiCase.getBody(), readAssertions(apiCase.getAssertionsJson()),
                readDataset(apiCase.getDatasetJson()), apiCase.getTimeoutSec(),
                apiCase.isEnabled(), apiCase.getCreatedBy(), apiCase.getCreatedAt(), apiCase.getUpdatedAt());
    }

    private Specification<ApiCase> keywordSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            predicates.add(cb.like(cb.lower(root.get("path")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private String normalizeMethod(String method) {
        String value = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(value)) {
            throw ApiException.badRequest("请求方法不支持: " + method);
        }
        return value;
    }

    private int timeout(Integer value) {
        if (value == null) {
            return 30;
        }
        if (value < 1 || value > 600) {
            throw ApiException.badRequest("timeoutSec 需在 1-600 之间");
        }
        return value;
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

    private String jsonDataset(List<Map<String, Object>> dataset) {
        if (dataset == null || dataset.isEmpty()) {
            return null;
        }
        if (dataset.size() > 200) {
            throw ApiException.badRequest("数据集最多 200 行");
        }
        try {
            return objectMapper.writeValueAsString(dataset);
        } catch (Exception e) {
            throw ApiException.badRequest("数据集序列化失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> readDataset(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readAssertions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
