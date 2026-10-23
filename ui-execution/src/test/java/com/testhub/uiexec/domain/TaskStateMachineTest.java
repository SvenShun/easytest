package com.testhub.uiexec.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStateMachineTest {

    @Test
    void pendingCanBeDispatchedFailedOrCancelled() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.DISPATCHED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.FAILED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.CANCELLED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.RUNNING)).isFalse();
    }

    @Test
    void dispatchedCanRunOrTerminateDirectly() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.RUNNING)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.SUCCESS)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.FAILED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.TIMEOUT)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.CANCELLED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.DISPATCHED, TaskStatus.PENDING)).isFalse();
    }

    @Test
    void runningCanOnlyTerminate() {
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.SUCCESS)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.FAILED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.TIMEOUT)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.CANCELLED)).isTrue();
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.DISPATCHED)).isFalse();
        assertThat(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.PENDING)).isFalse();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (TaskStatus terminal : new TaskStatus[]{TaskStatus.SUCCESS, TaskStatus.FAILED,
                TaskStatus.TIMEOUT, TaskStatus.CANCELLED}) {
            assertThat(TaskStateMachine.isTerminal(terminal)).isTrue();
            for (TaskStatus target : TaskStatus.values()) {
                assertThat(TaskStateMachine.canTransition(terminal, target))
                        .as("%s -> %s", terminal, target)
                        .isFalse();
            }
        }
    }

    @Test
    void duplicateTerminalCallbackIsRejectedByTheMachine() {
        // A repeated result callback is caught by the service (409) before this
        // guard; the machine itself must also refuse terminal -> terminal.
        assertThatThrownBy(() -> TaskStateMachine.requireTransition(TaskStatus.FAILED, TaskStatus.SUCCESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法任务状态流转");
    }

    @Test
    void illegalTransitionIsRejected() {
        assertThatThrownBy(() -> TaskStateMachine.requireTransition(TaskStatus.PENDING, TaskStatus.SUCCESS))
                .isInstanceOf(IllegalStateException.class);
    }
}
