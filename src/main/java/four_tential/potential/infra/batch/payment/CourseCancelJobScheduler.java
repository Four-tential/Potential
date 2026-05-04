package four_tential.potential.infra.batch.payment;

import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import four_tential.potential.domain.payment.repository.CourseCancelOutboxRepository;
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
public class CourseCancelJobScheduler {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final CourseCancelOutboxRepository courseCancelOutboxRepository;

    @Qualifier("courseCancelJob")
    private final Job courseCancelJob;

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "courseCancelJobScheduler", lockAtMostFor = "1m")
    public void runCourseCancelJob() {
        try {
            boolean running = !jobRepository.findRunningJobExecutions(courseCancelJob.getName()).isEmpty();
            if (running) {
                log.info("[SCHEDULER] courseCancelJob 실행 중 - 스킵");
                return;
            }

            boolean hasPending = courseCancelOutboxRepository.existsByStatus(CourseCancelOutboxStatus.PENDING);
            if (!hasPending) {
                log.info("[SCHEDULER] PENDING course_cancel_outbox 없음 - 스킵");
                return;
            }

            log.info("[SCHEDULER] courseCancelJob 실행 시작");
            jobOperator.start(courseCancelJob, new JobParameters());
        } catch (Exception e) {
            log.error("[SCHEDULER] courseCancelJob 실행 실패.", e);
        }
    }
}
