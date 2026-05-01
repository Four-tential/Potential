package four_tential.potential.infra.batch.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
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
class CourseCancelJobCompletionListenerTest {

    @InjectMocks
    private CourseCancelJobCompletionListener listener;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private Job refundTaskJob;

    @Mock
    private JobExecution jobExecution;

    @Test
    @DisplayName("Job1이 COMPLETED면 Job2를 즉시 시작한다")
    void afterJob_starts_refund_task_job_when_completed() throws Exception {
        given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob"))
                .willReturn(Collections.emptySet());

        listener.afterJob(jobExecution);

        verify(jobOperator).start(eq(refundTaskJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("Job1이 COMPLETED가 아니면 Job2를 시작하지 않는다")
    void afterJob_skips_when_not_completed() throws Exception {
        given(jobExecution.getStatus()).willReturn(BatchStatus.FAILED);

        listener.afterJob(jobExecution);

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("refundTaskJob이 이미 실행 중이면 즉시 트리거를 생략한다")
    void afterJob_skips_when_refund_task_job_is_already_running() throws Exception {
        given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob"))
                .willReturn(Collections.singleton(jobExecution));

        listener.afterJob(jobExecution);

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("Job2 시작이 실패해도 예외를 밖으로 던지지 않는다")
    void afterJob_swallows_exception_when_start_fails() throws Exception {
        given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
        given(refundTaskJob.getName()).willReturn("refundTaskJob");
        given(jobRepository.findRunningJobExecutions("refundTaskJob"))
                .willReturn(Collections.emptySet());
        doThrow(new RuntimeException("start fail"))
                .when(jobOperator).start(eq(refundTaskJob), any(JobParameters.class));

        listener.afterJob(jobExecution);

        verify(jobOperator).start(eq(refundTaskJob), any(JobParameters.class));
    }
}
