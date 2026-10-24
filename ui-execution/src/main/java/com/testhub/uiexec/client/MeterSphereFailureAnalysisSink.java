package com.testhub.uiexec.client;

import com.testhub.common.web.ApiException;
import com.testhub.uiexec.client.dto.FailureAnalysisPayload;
import com.testhub.uiexec.config.UiExecProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the failure analysis back to MeterSphere as a functional-case comment
 * ({@code POST /functional/case/comment/save}, source:
 * {@code FunctionalCaseCommentController.java} / {@code FunctionalCaseCommentRequest.java}).
 * Comments are additive, so the platform never overwrites a human remark.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "case-source", havingValue = "metersphere")
public class MeterSphereFailureAnalysisSink implements FailureAnalysisSink {

    private static final Logger log = LoggerFactory.getLogger(MeterSphereFailureAnalysisSink.class);

    private final UiExecProperties properties;
    private final RestClient client;

    public MeterSphereFailureAnalysisSink(UiExecProperties properties) {
        this.properties = properties;
        UiExecProperties.MeterSphere ms = properties.getMetersphere();
        RestClient.Builder builder = RestClient.builder().baseUrl(trimTrailingSlash(ms.getBaseUrl()));
        if (ms.getToken() != null && !ms.getToken().isBlank()) {
            builder.defaultHeader("X-AUTH-TOKEN", ms.getToken());
        }
        this.client = builder.build();
    }

    @Override
    public void write(String caseId, FailureAnalysisPayload payload) {
        UiExecProperties.MeterSphere ms = properties.getMetersphere();
        if (ms.getProjectId() == null || ms.getProjectId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "METERSPHERE_PROJECT_NOT_CONFIGURED",
                    "MeterSphere 模式必须配置 testhub.uiexec.metersphere.project-id");
        }
        StringBuilder content = new StringBuilder("TestHub 自动执行失败");
        if (payload.reasonCategory() != null) {
            content.append("\n失败分类: ").append(payload.reasonCategory());
        }
        if (payload.summary() != null) {
            content.append("\n摘要: ").append(payload.summary());
        }
        if (payload.reportUrl() != null) {
            content.append("\n失败轨迹报告: ").append(payload.reportUrl());
        }
        content.append("\n任务: ").append(payload.taskId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseId", caseId);
        body.put("content", content.toString());
        body.put("event", "COMMENT");
        body.put("projectId", ms.getProjectId());
        try {
            client.post().uri("/functional/case/comment/save").body(body).retrieve().toBodilessEntity();
            log.info("metersphere failure comment written caseId={} taskId={}", caseId, payload.taskId());
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "METERSPHERE_WRITE_FAILED",
                    "写入 MeterSphere 失败分析失败: " + e.getMessage());
        }
    }

    @Override
    public String backend() {
        return "metersphere";
    }

    private static String trimTrailingSlash(String value) {
        String base = value == null || value.isBlank() ? "http://127.0.0.1:8081" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
