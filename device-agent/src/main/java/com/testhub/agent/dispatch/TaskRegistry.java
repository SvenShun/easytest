package com.testhub.agent.dispatch;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/** In-memory task registry (agent has no database). */
@Component
public class TaskRegistry {

    private final ConcurrentMap<String, AgentTask> tasks = new ConcurrentHashMap<>();

    /** @return the created task, or {@code null} when the taskId is already known. */
    public AgentTask create(String taskId, String executorType) {
        AgentTask task = new AgentTask(taskId, executorType);
        return tasks.putIfAbsent(taskId, task) == null ? task : null;
    }

    public AgentTask get(String taskId) {
        return taskId == null ? null : tasks.get(taskId);
    }

    public Collection<AgentTask> all() {
        return tasks.values();
    }

    public int size() {
        return tasks.size();
    }
}
