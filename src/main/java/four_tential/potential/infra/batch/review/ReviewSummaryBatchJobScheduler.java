package four_tential.potential.infra.batch.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryBatchJobScheduler {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;

    @Qualifier("reviewSummaryBatchJob")
    private final Job reviewSummaryBatchJob;

    /**
     * 매일 새벽 3시에 전체 후기 기반 배치 재요약 실행
     * - 누적 갱신 방식의 왜곡 문제를 주기적으로 보정
     * - ShedLock으로 다중 인스턴스 환경에서 중복 실행 방지
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "reviewSummaryBatchJobScheduler", lockAtMostFor = "30m", lockAtLeastFor = "10m")
    public void runReviewSummaryBatchJob() {
        try {
            boolean running = !jobRepository.findRunningJobExecutions(reviewSummaryBatchJob.getName()).isEmpty();
            if (running) {
                log.info("[SCHEDULER] reviewSummaryBatchJob 실행 중 - 스킵");
                return;
            }

            log.info("[SCHEDULER] reviewSummaryBatchJob 실행 시작");
            jobOperator.start(reviewSummaryBatchJob, new JobParameters());

        } catch (Exception e) {
            log.error("[SCHEDULER] reviewSummaryBatchJob 실행 실패.", e);
        }
    }
}