package four_tential.potential.infra.batch.payment;

import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import four_tential.potential.domain.payment.repository.RefundTaskRepository;
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

/**
 * Job2 Listener 방식 사용. Job1이 끝나는 순간 바로 Job2 즉시 실행
 * 스케줄러는 남아 있는 PENDING task가 있을 때만 보조 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundTaskJobScheduler {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final RefundTaskRepository refundTaskRepository;

    @Qualifier("refundTaskJob")
    private final Job refundTaskJob;

    // Job1이 0분에 실행되면 Job2는 2분 후에 실행
    @Scheduled(cron = "0 2/5 * * * *")
    @SchedulerLock(name = "refundTaskJobScheduler", lockAtMostFor = "5m", lockAtLeastFor = "4m")
    public void runRefundTaskJob() {
        try {
            // 이미 실행 중이면 중복 실행 방지
            boolean running = !jobRepository.findRunningJobExecutions(refundTaskJob.getName()).isEmpty();
            if (running) {
                log.info("[SCHEDULER] refundTaskJob 실행 중 - 스킵");
                return;
            }

            // 할 일이 없으면 불필요 실행 방지
            boolean hasPending = refundTaskRepository.existsByStatus(RefundTaskStatus.PENDING);
            if (!hasPending) {
                log.info("[SCHEDULER] PENDING refund_task 없음 - 스킵");
                return;
            }

            log.info("[SCHEDULER] refundTaskJob 실행 시작");
            jobOperator.start(refundTaskJob, new JobParameters());

        } catch (Exception e) {
            log.error("[SCHEDULER] refundTaskJob 실행 실패.", e);
        }
    }
}
