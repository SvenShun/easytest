package com.easytest.core.feature.apiagent;

import com.easytest.core.feature.plan.ApiExecutionClient;
import com.easytest.core.feature.plan.PlanRunItem;
import com.easytest.core.feature.plan.PlanRunItemRepository;
import com.easytest.core.feature.plan.RunItemStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiDispatchQueueTest {

    @Test
    void dispatchesPendingTaskAndUpdatesRunItem() {
        ApiDispatchTaskRepository queue = mock(ApiDispatchTaskRepository.class);
        PlanRunItemRepository items = mock(PlanRunItemRepository.class);
        ApiExecutionClient client = mock(ApiExecutionClient.class);
        ApiDispatchTask task = new ApiDispatchTask("d1", "run1", "item1",
                "{\"planRunId\":\"run1\",\"caseId\":\"ac1\"}", Instant.now());
        PlanRunItem item = new PlanRunItem("item1", "run1", "ac1", "case", null,
                RunItemStatus.PENDING, Instant.now());
        when(queue.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(task));
        when(items.findById("item1")).thenReturn(Optional.of(item));
        when(queue.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.createTask(any())).thenReturn(new ApiExecutionClient.TaskCreated("task-1", "PENDING"));

        new ApiDispatchQueue(queue, items, client, new ObjectMapper()).dispatchPending();

        assertThat(item.getTaskId()).isEqualTo("task-1");
        assertThat(task.getStatus()).isEqualTo("DISPATCHED");
    }
}
