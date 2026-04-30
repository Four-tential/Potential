package four_tential.potential.infra.batch.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Job1 완료 시 Job2를 즉시 트리거하는 리스너
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCancelJobCompletionListener implements JobExecutionListener {

    private final JobOperator jobOperator;

    @Qualifier("refundTaskJob")
    private final Job refundTaskJob;

    /**
     * Job1이 완료될 때마다 호출된다.
     * Job1이 COMPLETED 일 때만 Job2를 트리거한다.
     * FAILED 인 경우 refund_task가 제대로 생성되지 않았을 수 있으므로
     * Job2를 실행하지 않는다.
     */
    @Override
    public void afterJob(JobExecution jobExecution) {

        // Job1 상태 확인
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("[LISTENER] courseCancelJob 완료 → refundTaskJob 트리거");

            try {
                // Job1 완료 직후 즉시 Job2 실행
                jobOperator.start(refundTaskJob, new JobParameters());
                log.info("[LISTENER] refundTaskJob 트리거 성공");

            } catch (Exception e) {
                // 다음 RefundTaskJobScheduler 주기에 처리된다
                log.error("[LISTENER] refundTaskJob 트리거 실패. " + "다음 스케줄 주기에 처리됩니다.", e);
            }

        } else {
            log.warn("[LISTENER] courseCancelJob 비정상 종료 (status={}). " + "refundTaskJob 트리거 생략.", jobExecution.getStatus());
        }
    }
}
