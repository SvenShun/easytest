package com.easytest.core.feature.apicase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiCaseDtos {

    private ApiCaseDtos() {
    }

    public record ApiCaseRequest(
            @NotBlank(message = "用例名称不能为空") @Size(max = 255) String name,
            @NotBlank(message = "请求方法不能为空") String method,
            @Size(max = 1000) String baseUrl,
            @NotBlank(message = "请求路径不能为空") @Size(max = 2000) String path,
            Map<String, String> headers,
            Map<String, String> query,
            String body,
            List<Map<String, Object>> assertions,
            List<Map<String, Object>> dataset,
            Integer timeoutSec,
            Boolean enabled,
            Map<String, String> inputContract,
            Map<String, String> outputContract) {

        public ApiCaseRequest(String name, String method, String baseUrl, String path,
                              Map<String, String> headers, Map<String, String> query, String body,
                              List<Map<String, Object>> assertions, List<Map<String, Object>> dataset,
                              Integer timeoutSec, Boolean enabled) {
            this(name, method, baseUrl, path, headers, query, body, assertions, dataset, timeoutSec,
                    enabled, null, null);
        }
    }

    public record ApiCaseView(String id, String name, String method, String baseUrl, String path,
                              Map<String, String> headers, Map<String, String> query, String body,
                              List<Map<String, Object>> assertions, List<Map<String, Object>> dataset,
                              int timeoutSec, boolean enabled, Map<String, String> inputContract,
                              Map<String, String> outputContract, String createdBy, Instant createdAt,
                              Instant updatedAt) {
    }

    public record ApiCaseListView(List<ApiCaseView> items, long total, int page, int size) {
    }
}
