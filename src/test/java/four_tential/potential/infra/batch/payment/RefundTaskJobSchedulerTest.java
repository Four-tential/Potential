package four_tential.potential.infra.batch.payment;

import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import four_tential.potential.domain.payment.repository.RefundTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundTaskJobSchedulerTest {

    @InjectMocks
    private RefundTaskJobScheduler refundTaskJobScheduler;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RefundTaskRepository refundTaskRepository;

    @Mock
    private Job refundTaskJob;

    @Test
    @DisplayName("이미 refundTaskJob이 실행 중이면 새로 시작하지 않는다")
    void runRefundTaskJob_skips_when_job_is_already_running() throws Exception {
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob"))
                .willReturn(Collections.singleton(org.mockito.Mockito.mock(org.springframework.batch.core.job.JobExecution.class)));

        refundTaskJobScheduler.runRefundTaskJob();

        verify(refundTaskRepository, never()).existsByStatus(any());
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 중이 아니어도 PENDING task가 없으면 시작하지 않는다")
    void runRefundTaskJob_skips_when_no_pending_task() throws Exception {
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob")).willReturn(Collections.emptySet());
        given(refundTaskRepository.existsByStatus(RefundTaskStatus.PENDING)).willReturn(false);

        refundTaskJobScheduler.runRefundTaskJob();

        verify(refundTaskRepository).existsByStatus(RefundTaskStatus.PENDING);
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 중이 아니고 PENDING task가 있으면 refundTaskJob을 시작한다")
    void runRefundTaskJob_starts_when_pending_task_exists() throws Exception {
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob")).willReturn(Collections.emptySet());
        given(refundTaskRepository.existsByStatus(RefundTaskStatus.PENDING)).willReturn(true);

        refundTaskJobScheduler.runRefundTaskJob();

        verify(jobOperator).start(eq(refundTaskJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("스케줄러 실행 중 예외가 나도 밖으로 던지지 않는다")
    void runRefundTaskJob_swallows_exception_when_start_fails() throws Exception {
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob")).willReturn(Collections.emptySet());
        given(refundTaskRepository.existsByStatus(RefundTaskStatus.PENDING)).willReturn(true);
        doThrow(new RuntimeException("start fail"))
                .when(jobOperator).start(eq(refundTaskJob), any(JobParameters.class));

        refundTaskJobScheduler.runRefundTaskJob();

        verify(jobOperator).start(eq(refundTaskJob), any(JobParameters.class));
    }
}
