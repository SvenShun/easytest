package com.testhub.core.feature.job;

import com.testhub.common.security.AuthPrincipal;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.security.Role;
import com.testhub.common.web.ApiException;
import com.testhub.core.feature.plan.PlanRunService;
import com.testhub.core.feature.plan.TestPlan;
import com.testhub.core.feature.plan.TestPlanRepository;
import com.testhub.core.feature.plan.TriggerRequest;
import com.testhub.core.feature.plan.TriggerResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private final TestJobRepository jobs = mock(TestJobRepository.class);
    private final TestPlanRepository plans = mock(TestPlanRepository.class);
    private final PlanRunService planRunService = mock(PlanRunService.class);
    private final JobService service = new JobService(jobs, plans, planRunService);

    @AfterEach
    void clear() {
        CurrentUser.clear();
    }

    @Test
    void createsJobWithNextRunAndValidatesCron() {
        CurrentUser.set(new AuthPrincipal("u-tester", "tester", "Tester", Role.TESTER));
        when(plans.existsById("plan-1")).thenReturn(true);
        when(jobs.save(any(TestJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(plans.findById("plan-1")).thenReturn(Optional.of(plan()));

        JobDtos.JobView view = service.create(new JobDtos.JobRequest(
                "nightly", "plan-1", "0 0 2 * * *", null, "android,adb", true));

        assertThat(view.nextRunAt()).isNotNull();
        assertThat(view.planName()).isEqualTo("回归计划");
        assertThat(view.deviceTags()).isEqualTo("android,adb");

        assertThatThrownBy(() -> service.create(new JobDtos.JobRequest(
                "bad", "plan-1", "not-a-cron", null, null, true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cron");
    }

    @Test
    void runNowTriggersPlanWithDeviceAndTags() {
        TestJob job = new TestJob("job-1", "nightly", "plan-1", "0 0 2 * * *",
                "device-1", "android", true, "tester", Instant.now(), Instant.now());
        when(jobs.findById("job-1")).thenReturn(Optional.of(job));
        when(plans.findById("plan-1")).thenReturn(Optional.of(plan()));
        when(jobs.save(any(TestJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planRunService.trigger(any(), any())).thenReturn(new TriggerResponse("run-1", List.of()));

        JobDtos.JobView view = service.runNow("job-1");

        ArgumentCaptor<TriggerRequest> captor = ArgumentCaptor.forClass(TriggerRequest.class);
        verify(planRunService).trigger(org.mockito.ArgumentMatchers.eq("plan-1"), captor.capture());
        assertThat(captor.getValue().targetDeviceId()).isEqualTo("device-1");
        assertThat(captor.getValue().targetTags()).containsExactly("android");
        assertThat(view.lastRunId()).isEqualTo("run-1");
    }

    private TestPlan plan() {
        return new TestPlan("plan-1", "回归计划", "desc", "READY", "tester", Instant.now(), Instant.now());
    }
}
