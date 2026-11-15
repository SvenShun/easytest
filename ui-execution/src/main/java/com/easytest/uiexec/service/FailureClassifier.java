package com.easytest.uiexec.service;

import com.easytest.uiexec.domain.ExecutorType;
import com.easytest.uiexec.domain.FailureCategory;
import com.easytest.uiexec.domain.TaskStatus;
import org.springframework.stereotype.Component;

/**
 * Failure classification priority (docs/V1-SCOPE.md §6):
 * agent/runtime reported category → status derivation (TIMEOUT, CANCELLED) →
 * executor fallback (SCRIPT → SCRIPT_FAILED, AI_RUNTIME → ACTION_FAILED).
 */
@Component
public class FailureClassifier {

    public FailureCategory classify(TaskStatus terminalStatus, ExecutorType executorType,
                                    FailureCategory reported) {
        if (terminalStatus == null || terminalStatus == TaskStatus.SUCCESS) {
            return null;
        }
        if (reported != null) {
            return reported;
        }
        if (terminalStatus == TaskStatus.TIMEOUT) {
            return FailureCategory.TIMEOUT;
        }
        if (terminalStatus == TaskStatus.CANCELLED) {
            return FailureCategory.MANUAL_INTERRUPT;
        }
        return executorType == ExecutorType.SCRIPT ? FailureCategory.SCRIPT_FAILED : FailureCategory.ACTION_FAILED;
    }
}
