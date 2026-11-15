package com.easytest.core.feature.apiagent;

import com.easytest.common.util.Ids;
import com.easytest.common.web.ApiException;
import com.easytest.core.feature.plan.ApiExecutionClient;
import com.easytest.core.feature.plan.PlanRunItem;
import com.easytest.core.feature.plan.PlanRunItemRepository;
import com.easytest.core.feature.plan.RunItemStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable outbox for API tasks waiting for an agent slot. */
@Component
public class ApiDispatchQueue {

    private static final Logger log = LoggerFactory.getLogger(ApiDispatchQueue.class);
    private static final int MAX_ATTEMPTS = 60;

    private final ApiDispatchTaskRepository queue;
    private final PlanRunItemRepository runItems;
    private final ApiExecutionClient apiExecutionClient;
    private final ObjectMapper objectMapper;
    private final String owner = "core-" + UUID.randomUUID().toString().substring(0, 8);
    private static final Duration LEASE = Duration.ofSeconds(30);

    public ApiDispatchQueue(ApiDispatchTaskRepository queue, PlanRunItemRepository runItems,
                            ApiExecutionClient apiExecutionClient, ObjectMapper objectMapper) {
        this.queue = queue;
        this.runItems = runItems;
        this.apiExecutionClient = apiExecutionClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApiDispatchTask enqueue(String planRunId, String planRunItemId, Map<String, Object> payload) {
        try {
            return queue.save(new ApiDispatchTask(Ids.newId("api-dispatch"), planRunId, planRunItemId,
                    objectMapper.writeValueAsString(payload), Instant.now()));
        } catch (Exception e) {
            throw ApiException.internal("API 调度队列序列化失败: " + e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${easytest.core.api-dispatch-poll-ms:1000}")
    @Transactional
    public void dispatchPending() {
        Instant now = Instant.now();
        List<ApiDispatchTask> candidates = queue.findClaimable(now);
        for (ApiDispatchTask task : candidates.stream().limit(20).toList()) {
            if (queue.claim(task.getId(), owner, now.plus(LEASE), now) == 0) {
                continue;
            }
            PlanRunItem item = runItems.findById(task.getPlanRunItemId()).orElse(null);
            if (item == null || item.getStatus() != RunItemStatus.PENDING || item.getTaskId() != null) {
                task.setStatus("CANCELLED");
                task.setClaimedBy(owner);
                task.setLeaseUntil(null);
                task.setUpdatedAt(Instant.now());
                queue.save(task);
                continue;
            }
            try {
                Map<String, Object> payload = objectMapper.readValue(task.getPayloadJson(),
                        new TypeReference<Map<String, Object>>() { });
                ApiExecutionClient.TaskCreated created = apiExecutionClient.createTask(payload);
                item.setTaskId(created.taskId());
                RunItemStatus status = RunItemStatus.parse(created.status());
                item.setStatus(status == null ? RunItemStatus.PENDING : status);
                item.setError(null);
                item.setUpdatedAt(Instant.now());
                runItems.save(item);
                task.setStatus("DISPATCHED");
                task.setClaimedBy(owner);
                task.setLeaseUntil(null);
                task.setLastError(null);
                task.setUpdatedAt(Instant.now());
                queue.save(task);
                log.info("queued API task dispatched planRun={} item={} task={}", task.getPlanRunId(),
                        task.getPlanRunItemId(), created.taskId());
            } catch (Exception e) {
                int attempts = task.getAttempts() + 1;
                task.setAttempts(attempts);
                task.setLastError(truncate(e.getMessage()));
                task.setUpdatedAt(Instant.now());
                if (attempts >= MAX_ATTEMPTS) {
                    task.setStatus("FAILED");
                    item.setStatus(RunItemStatus.FAILED);
                    item.setError(truncate("API Agent 调度超时: " + e.getMessage()));
                    item.setUpdatedAt(Instant.now());
                    runItems.save(item);
                } else {
                    task.setStatus("PENDING");
                    task.setClaimedBy(null);
                    task.setLeaseUntil(null);
                }
                queue.save(task);
            }
        }
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() > 1900 ? message.substring(0, 1900) : message;
    }
}
