package four_tential.potential.infra.batch.payment;

import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import four_tential.potential.domain.payment.repository.CourseCancelOutboxRepository;
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
class CourseCancelJobSchedulerTest {

    @InjectMocks
    private CourseCancelJobScheduler courseCancelJobScheduler;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CourseCancelOutboxRepository courseCancelOutboxRepository;

    @Mock
    private Job courseCancelJob;

    @Test
    @DisplayName("이미 courseCancelJob이 실행 중이면 새로 시작하지 않는다")
    void runCourseCancelJob_skips_when_job_is_already_running() throws Exception {
        given(courseCancelJob.getName()).willReturn("courseCancelJob");
        given(jobRepository.findRunningJobExecutions("courseCancelJob"))
                .willReturn(Collections.singleton(org.mockito.Mockito.mock(org.springframework.batch.core.job.JobExecution.class)));

        courseCancelJobScheduler.runCourseCancelJob();

        verify(courseCancelOutboxRepository, never()).existsByStatus(any());
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 중이 아니어도 PENDING outbox가 없으면 시작하지 않는다")
    void runCourseCancelJob_skips_when_no_pending_outbox() throws Exception {
        given(courseCancelJob.getName()).willReturn("courseCancelJob");
        given(jobRepository.findRunningJobExecutions("courseCancelJob")).willReturn(Collections.emptySet());
        given(courseCancelOutboxRepository.existsByStatus(CourseCancelOutboxStatus.PENDING)).willReturn(false);

        courseCancelJobScheduler.runCourseCancelJob();

        verify(courseCancelOutboxRepository).existsByStatus(CourseCancelOutboxStatus.PENDING);
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 중이 아니고 PENDING outbox가 있으면 courseCancelJob을 시작한다")
    void runCourseCancelJob_starts_when_pending_outbox_exists() throws Exception {
        given(courseCancelJob.getName()).willReturn("courseCancelJob");
        given(jobRepository.findRunningJobExecutions("courseCancelJob")).willReturn(Collections.emptySet());
        given(courseCancelOutboxRepository.existsByStatus(CourseCancelOutboxStatus.PENDING)).willReturn(true);

        courseCancelJobScheduler.runCourseCancelJob();

        verify(jobOperator).start(eq(courseCancelJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("스케줄러 실행 중 예외가 나도 밖으로 던지지 않는다")
    void runCourseCancelJob_swallows_exception_when_start_fails() throws Exception {
        given(courseCancelJob.getName()).willReturn("courseCancelJob");
        given(jobRepository.findRunningJobExecutions("courseCancelJob")).willReturn(Collections.emptySet());
        given(courseCancelOutboxRepository.existsByStatus(CourseCancelOutboxStatus.PENDING)).willReturn(true);
        doThrow(new RuntimeException("start fail"))
                .when(jobOperator).start(eq(courseCancelJob), any(JobParameters.class));

        courseCancelJobScheduler.runCourseCancelJob();

        verify(jobOperator).start(eq(courseCancelJob), any(JobParameters.class));
    }
}
