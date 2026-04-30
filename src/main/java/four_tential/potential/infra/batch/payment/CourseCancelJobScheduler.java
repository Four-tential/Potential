package four_tential.potential.infra.batch.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCancelJobScheduler {

    private final JobOperator jobOperator;

    @Qualifier("courseCancelJob")
    private final Job courseCancelJob;

    @Scheduled(cron = "0 */5 * * * *")
    public void runCourseCancelJob() {
        try {
            log.info("[SCHEDULER] courseCancelJob 실행 시작");
            jobOperator.start(courseCancelJob, new JobParameters());
        } catch (Exception e) {
            log.error("[SCHEDULER] courseCancelJob 실행 실패.", e);
        }
    }
}
