package com.testhub.uiexec.events;

import com.testhub.uiexec.domain.DeviceEntity;
import com.testhub.uiexec.domain.TaskEntity;

/** Publishes task lifecycle events; transport is selected by {@code testhub.uiexec.event-bus}. */
public interface TaskEventPublisher {

    void dispatched(TaskEntity task, DeviceEntity device);

    void finished(TaskEntity task, DeviceEntity device);
}
