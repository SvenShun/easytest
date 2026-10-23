package com.testhub.agent.dispatch;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.testhub.agent.config.AgentProperties;

/**
 * Single-shot final result reporting. Both executors use this so a task can
 * never report two terminal results (cancel + completion race).
 */
@Component
public class TaskReporter {

    private static final Logger log = LoggerFactory.getLogger(TaskReporter.class);

    private final CallbackClient callbacks;
    private final AgentProperties properties;

    public TaskReporter(CallbackClient callbacks, AgentProperties properties) {
        this.callbacks = callbacks;
        this.properties = properties;
    }

    public boolean report(AgentTask task, TaskDispatchRequest request, String status,
            String reasonCategory, String summary, List<Payloads.Artifact> artifacts) {
        if (!task.markResultReported()) {
            log.info("task {} result already reported, skipping duplicate {}", task.getTaskId(), status);
            return false;
        }
        task.setStatus(status);
        String token = request.callbackToken(properties.getToken());
        String resultUrl = request.callbacks() == null ? null : request.callbacks().resultUrl();
        Payloads.TaskResult payload = new Payloads.TaskResult(status, reasonCategory, summary,
                reasonCategory, artifacts == null ? List.of() : artifacts);
        boolean posted = callbacks.postResult(resultUrl, token, payload);
        log.info("task {} finished with {} ({})", task.getTaskId(), status, summary);
        return posted;
    }
}
