package com.testhub.uiexec.events;

import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default single-node transport: events are logged with the same fields that
 * the Kafka payload carries, so log shipping can substitute for a broker in V1.
 */
@Component
@ConditionalOnProperty(prefix = "testhub.uiexec", name = "event-bus", havingValue = "local", matchIfMissing = true)
public class LocalTaskEventPublisher implements TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalTaskEventPublisher.class);

    @Override
    public void dispatched(TaskEntity task, DeviceEntity device) {
        log.info("task-event type=TASK_DISPATCHED taskId={} caseId={} executor={} device={} serial={}",
                task.getId(), task.getCaseId(), task.getExecutorType(),
                device == null ? task.getDeviceId() : device.getId(),
                device == null ? task.getDeviceSerial() : device.getSerial());
    }

    @Override
    public void finished(TaskEntity task, DeviceEntity device) {
        log.info("task-event type=TASK_FINISHED taskId={} caseId={} executor={} status={} failure={} report={}",
                task.getId(), task.getCaseId(), task.getExecutorType(), task.getStatus(),
                task.getFailureReason(), task.getFailureReportUrl());
    }
}
