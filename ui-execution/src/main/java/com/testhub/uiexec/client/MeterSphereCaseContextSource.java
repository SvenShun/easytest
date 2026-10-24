package com.testhub.uiexec.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.testhub.common.web.ApiException;
import com.testhub.uiexec.client.dto.ExecutionContext;
import com.testhub.uiexec.config.UiExecProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * MeterSphere v3 adapter (opt-in with {@code testhub.uiexec.case-source=metersphere}).
 *
 * <p>Reads a functional case through MeterSphere's own API:
 * {@code GET /functional/case/detail/{id}} (source:
 * {@code backend/services/case-management/.../FunctionalCaseController.java},
 * {@code FunctionalCaseDetailDTO.java}). MeterSphere has no native BDD model, so
 * the mapping is explicit and configurable:
 * <ul>
 *   <li>title ← {@code name}</li>
 *   <li>given ← {@code prerequisite} (fallback: 已打开待测应用)</li>
 *   <li>when  ← {@code textDescription}, else {@code steps}</li>
 *   <li>then  ← {@code expectedResult}</li>
 * </ul>
 * A BDD-shaped custom field can replace this mapping later without touching the
 * execution pipeline.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "case-source", havingValue = "metersphere")
public class MeterSphereCaseContextSource implements CaseContextSource {

    private static final Logger log = LoggerFactory.getLogger(MeterSphereCaseContextSource.class);

    private final UiExecProperties properties;
    private final RestClient client;

    public MeterSphereCaseContextSource(UiExecProperties properties) {
        this.properties = properties;
        UiExecProperties.MeterSphere ms = properties.getMetersphere();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(trimTrailingSlash(ms.getBaseUrl()))
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(5_000);
                    setReadTimeout(10_000);
                }});
        if (ms.getToken() != null && !ms.getToken().isBlank()) {
            builder.defaultHeader("X-AUTH-TOKEN", ms.getToken());
        }
        this.client = builder.build();
    }

    @Override
    public ExecutionContext fetch(String caseId) {
        MeterSphereCase msCase;
        try {
            msCase = client.get()
                    .uri("/functional/case/detail/{id}", caseId)
                    .retrieve()
                    .body(MeterSphereCase.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "METERSPHERE_CASE_NOT_FOUND",
                        "MeterSphere 中不存在用例: " + caseId);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "METERSPHERE_UNAVAILABLE",
                    "读取 MeterSphere 用例失败: HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "METERSPHERE_UNAVAILABLE",
                    "无法连接 MeterSphere: " + e.getMessage());
        }
        if (msCase == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "METERSPHERE_EMPTY_RESPONSE",
                    "MeterSphere 返回空用例");
        }
        String reviewStatus = reviewStatus(msCase.reviewStatus());
        ExecutionContext.Bdd bdd = new ExecutionContext.Bdd(
                text(msCase.prerequisite(), "已打开待测应用"),
                text(firstNonBlank(msCase.textDescription(), msCase.steps()), "执行用例"),
                text(msCase.expectedResult(), "用例执行成功"));
        log.info("metersphere case fetched caseId={} name={} review={}", caseId, msCase.name(), reviewStatus);
        return new ExecutionContext(caseId, msCase.name(), reviewStatus, bdd, null, null);
    }

    @Override
    public String backend() {
        return "metersphere";
    }

    private String reviewStatus(String msStatus) {
        UiExecProperties.MeterSphere ms = properties.getMetersphere();
        if (!ms.isRequireReview()) {
            return "APPROVED";
        }
        String approved = ms.getApprovedReviewStatuses() == null ? "COMPLETED" : ms.getApprovedReviewStatuses();
        for (String value : approved.split(",")) {
            if (value.trim().equalsIgnoreCase(msStatus)) {
                return "APPROVED";
            }
        }
        return msStatus == null || msStatus.isBlank() ? "DRAFT" : msStatus;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String text(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() > 4000 ? result.substring(0, 4000) : result;
    }

    private static String trimTrailingSlash(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:8081" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MeterSphereCase(String id, String name, String prerequisite, String textDescription, String steps,
                           String expectedResult, String reviewStatus, String projectId) {
    }
}
