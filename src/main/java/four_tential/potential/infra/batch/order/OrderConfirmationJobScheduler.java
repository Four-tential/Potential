package four_tential.potential.infra.batch.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmationJobScheduler {

    private final JobOperator jobOperator;

    @Qualifier("orderConfirmationJob")
    private final Job orderConfirmationJob;

    /**
     * 매일 00:10에 실행: 환불 기간이 지난 PAID 주문을 CONFIRMED로 변경
     */
    @Scheduled(cron = "0 10 0 * * *")
    @SchedulerLock(name = "orderConfirmationJobScheduler", lockAtMostFor = "30m", lockAtLeastFor = "5m")
    public void confirmOrders() {
        log.info("결제 완료 주문 자동 확정 스케줄러 시작");

        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("now", LocalDateTime.now().toString())
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobOperator.start(orderConfirmationJob, jobParameters);
            
        } catch (Exception e) {
            log.error("주문 확정 스케줄러 실행 중 예외 발생", e);
        }
    }
}
