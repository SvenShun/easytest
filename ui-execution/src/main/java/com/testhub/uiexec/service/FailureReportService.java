package com.testhub.uiexec.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.util.Ids;
import com.testhub.uiexec.artifact.ArtifactStore;
import com.testhub.uiexec.domain.ArtifactEntity;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.FailureReportEntity;
import com.testhub.uiexec.domain.StepEntity;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.repository.ArtifactRepository;
import com.testhub.uiexec.repository.FailureReportRepository;
import com.testhub.uiexec.repository.StepRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the JSON failure trajectory report (docs/API-CONTRACT.md §2.7),
 * uploads it through the {@link ArtifactStore} and records the pointer row.
 */
@Service
public class FailureReportService {

    private static final int MAX_TIMELINE_STEPS = 500;

    private final FailureReportRepository reports;
    private final StepRepository steps;
    private final ArtifactRepository artifacts;
    private final ArtifactStore store;
    private final ObjectMapper objectMapper;

    public FailureReportService(FailureReportRepository reports, StepRepository steps, ArtifactRepository artifacts,
                                ArtifactStore store, ObjectMapper objectMapper) {
        this.reports = reports;
        this.steps = steps;
        this.artifacts = artifacts;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public FailureReportEntity generate(TaskEntity task, FailureCategory category, String summary) {
        String reportId = Ids.newId("report");
        Instant generatedAt = Instant.now();
        List<StepEntity> timeline = steps.findByTaskIdOrderBySeqAsc(task.getId(),
                PageRequest.of(0, MAX_TIMELINE_STEPS));
        List<ArtifactEntity> artifactRows = artifacts.findByTaskIdOrderByCreatedAtAsc(task.getId());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportId", reportId);
        report.put("taskId", task.getId());
        report.put("caseId", task.getCaseId());
        report.put("caseTitle", task.getCaseTitle());
        report.put("status", task.getStatus() == null ? null : task.getStatus().name());
        report.put("reasonCategory", category == null ? null : category.name());
        report.put("summary", summary);
        report.put("generatedAt", generatedAt.toString());
        report.put("generatedBy", "ui-execution");

        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("executorType", task.getExecutorType() == null ? null : task.getExecutorType().name());
        environment.put("deviceId", task.getDeviceId());
        environment.put("deviceSerial", task.getDeviceSerial());
        environment.put("agentId", task.getAgentId());
        environment.put("skillsCommit", task.getSkillsCommit());
        report.put("environment", environment);

        List<Map<String, Object>> timelineJson = new ArrayList<>();
        for (StepEntity step : timeline) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", step.getSeq());
            item.put("status", step.getStatus());
            item.put("decision", step.getDecision());
            item.put("action", step.getAction());
            item.put("elapsedMs", step.getElapsedMs());
            item.put("screenshotUrl", step.getScreenshotUrl());
            timelineJson.add(item);
        }
        report.put("timeline", timelineJson);

        List<Map<String, Object>> artifactJson = new ArrayList<>();
        for (ArtifactEntity artifact : artifactRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", artifact.getName());
            item.put("type", artifact.getType());
            item.put("url", artifact.getUrl());
            artifactJson.add(item);
        }
        report.put("artifacts", artifactJson);

        byte[] payload;
        try {
            payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("生成失败轨迹报告失败: " + e.getMessage(), e);
        }
        String relative = store.put(task.getId(), "failure-report.json", payload);
        String reportUrl = store.publicUrl(relative);

        FailureReportEntity entity = new FailureReportEntity(reportId, task.getId(), reportUrl, category, summary,
                generatedAt);
        return reports.save(entity);
    }

    public Optional<FailureReportEntity> latest(String taskId) {
        return reports.findFirstByTaskIdOrderByCreatedAtDesc(taskId);
    }
}
