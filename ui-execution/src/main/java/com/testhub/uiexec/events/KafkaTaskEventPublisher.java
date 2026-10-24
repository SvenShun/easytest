package com.testhub.uiexec.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.util.Ids;
import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka transport (opt-in with {@code testhub.uiexec.event-bus=kafka}).
 *
 * <p>Topics follow the architecture document: {@code ui-execution.task.dispatch}
 * and {@code ui-execution.task.result}. V1 keeps task dispatch on HTTP and uses
 * Kafka as the task lifecycle event stream; moving dispatch itself onto Kafka is
 * the documented next step. Publish failures are logged and never block a task.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "event-bus", havingValue = "kafka")
public class KafkaTaskEventPublisher implements TaskEventPublisher {

    public static final String DISPATCH_TOPIC = "ui-execution.task.dispatch";
    public static final String RESULT_TOPIC = "ui-execution.task.result";

    private static final Logger log = LoggerFactory.getLogger(KafkaTaskEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaTaskEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void dispatched(TaskEntity task, DeviceEntity device) {
        send(DISPATCH_TOPIC, TaskEvent.dispatched(task, device, Ids.newId("evt")));
    }

    @Override
    public void finished(TaskEntity task, DeviceEntity device) {
        send(RESULT_TOPIC, TaskEvent.finished(task, device, Ids.newId("evt")));
    }

    private void send(String topic, TaskEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("task-event 序列化失败 topic={} taskId={}: {}", topic, event.taskId(), e.getMessage());
            return;
        }
        kafkaTemplate.send(topic, event.taskId(), payload).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("task-event 发送失败 topic={} taskId={}: {}", topic, event.taskId(), error.getMessage());
            } else if (log.isDebugEnabled()) {
                log.debug("task-event sent topic={} taskId={} offset={}", topic, event.taskId(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
