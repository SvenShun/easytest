package com.testhub.uiexec.service;

import com.testhub.uiexec.domain.ExecutorType;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureCategoryTest {

    private final FailureClassifier classifier = new FailureClassifier();

    @Test
    void agentReportedCategoryWinsOverStatusAndExecutorFallback() {
        assertThat(classifier.classify(TaskStatus.FAILED, ExecutorType.AI_RUNTIME,
                FailureCategory.MODEL_DECISION_FAILED)).isEqualTo(FailureCategory.MODEL_DECISION_FAILED);
        assertThat(classifier.classify(TaskStatus.FAILED, ExecutorType.SCRIPT,
                FailureCategory.ELEMENT_NOT_FOUND)).isEqualTo(FailureCategory.ELEMENT_NOT_FOUND);
        assertThat(classifier.classify(TaskStatus.TIMEOUT, ExecutorType.AI_RUNTIME,
                FailureCategory.SKILLS_UNAVAILABLE)).isEqualTo(FailureCategory.SKILLS_UNAVAILABLE);
    }

    @Test
    void terminalStatusDerivesTimeoutAndManualInterrupt() {
        assertThat(classifier.classify(TaskStatus.TIMEOUT, ExecutorType.SCRIPT, null))
                .isEqualTo(FailureCategory.TIMEOUT);
        assertThat(classifier.classify(TaskStatus.CANCELLED, ExecutorType.AI_RUNTIME, null))
                .isEqualTo(FailureCategory.MANUAL_INTERRUPT);
    }

    @Test
    void executorFallbackForFailedTasks() {
        assertThat(classifier.classify(TaskStatus.FAILED, ExecutorType.SCRIPT, null))
                .isEqualTo(FailureCategory.SCRIPT_FAILED);
        assertThat(classifier.classify(TaskStatus.FAILED, ExecutorType.AI_RUNTIME, null))
                .isEqualTo(FailureCategory.ACTION_FAILED);
    }

    @Test
    void systemCategoryIsPreserved() {
        assertThat(classifier.classify(TaskStatus.FAILED, ExecutorType.AI_RUNTIME,
                FailureCategory.EXECUTOR_UNAVAILABLE)).isEqualTo(FailureCategory.EXECUTOR_UNAVAILABLE);
    }

    @Test
    void successHasNoFailureCategory() {
        assertThat(classifier.classify(TaskStatus.SUCCESS, ExecutorType.SCRIPT, null)).isNull();
        assertThat(classifier.classify(TaskStatus.SUCCESS, ExecutorType.AI_RUNTIME,
                FailureCategory.ACTION_FAILED)).isNull();
    }

    @Test
    void parseIsLenientAndCaseInsensitive() {
        assertThat(FailureCategory.parse("timeout")).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(FailureCategory.parse("not-a-category")).isNull();
        assertThat(FailureCategory.parse(" ")).isNull();
    }
}
