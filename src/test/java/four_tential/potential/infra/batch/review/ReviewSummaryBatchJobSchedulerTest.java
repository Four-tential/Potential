package four_tential.potential.infra.batch.review;

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
class ReviewSummaryBatchJobSchedulerTest {

    @InjectMocks
    private ReviewSummaryBatchJobScheduler reviewSummaryBatchJobScheduler;

    @Mock private JobOperator jobOperator;
    @Mock private JobRepository jobRepository;
    @Mock private Job reviewSummaryBatchJob;

    @Test
    @DisplayName("이미 reviewSummaryBatchJob이 실행 중이면 새로 시작하지 않는다")
    void skipsWhenJobIsAlreadyRunning() throws Exception {
        given(reviewSummaryBatchJob.getName()).willReturn("reviewSummaryBatchJob");
        given(jobRepository.findRunningJobExecutions("reviewSummaryBatchJob"))
                .willReturn(Collections.singleton(
                        org.mockito.Mockito.mock(org.springframework.batch.core.job.JobExecution.class)
                ));

        reviewSummaryBatchJobScheduler.runReviewSummaryBatchJob();

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 중이 아니면 reviewSummaryBatchJob을 시작한다")
    void startsWhenNotRunning() throws Exception {
        given(reviewSummaryBatchJob.getName()).willReturn("reviewSummaryBatchJob");
        given(jobRepository.findRunningJobExecutions("reviewSummaryBatchJob"))
                .willReturn(Collections.emptySet());

        reviewSummaryBatchJobScheduler.runReviewSummaryBatchJob();

        verify(jobOperator).start(eq(reviewSummaryBatchJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("스케줄러 실행 중 예외가 나도 밖으로 던지지 않는다")
    void swallowsExceptionWhenStartFails() throws Exception {
        given(reviewSummaryBatchJob.getName()).willReturn("reviewSummaryBatchJob");
        given(jobRepository.findRunningJobExecutions("reviewSummaryBatchJob"))
                .willReturn(Collections.emptySet());
        doThrow(new RuntimeException("start fail"))
                .when(jobOperator).start(eq(reviewSummaryBatchJob), any(JobParameters.class));

        reviewSummaryBatchJobScheduler.runReviewSummaryBatchJob();

        verify(jobOperator).start(eq(reviewSummaryBatchJob), any(JobParameters.class));
    }
}