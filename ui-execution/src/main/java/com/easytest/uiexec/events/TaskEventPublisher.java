package com.easytest.uiexec.events;

import com.easytest.uiexec.domain.DeviceEntity;
import com.easytest.uiexec.domain.TaskEntity;

/** Publishes task lifecycle events; transport is selected by {@code easytest.uiexec.event-bus}. */
public interface TaskEventPublisher {

    void dispatched(TaskEntity task, DeviceEntity device);

    void finished(TaskEntity task, DeviceEntity device);
}
