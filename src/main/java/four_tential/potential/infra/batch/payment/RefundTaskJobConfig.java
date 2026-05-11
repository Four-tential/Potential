package four_tential.potential.infra.batch.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import four_tential.potential.domain.payment.repository.RefundTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RefundTaskJobConfig {

    private final JobRepository jobRepository;
    private final RefundTaskRepository refundTaskRepository;
    private final RefundFacade refundFacade;

    /** 최대 재시도 횟수 */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 1차 실패 후 재시도 대기 시간 (분)
     * 지수 백오프: 1차 5분, 2차 10분, 3차 20분
     */
    private static final long INITIAL_RETRY_DELAY_MINUTES = 5L;

    @Bean
    public Job refundTaskJob() {
        return new JobBuilder("refundTaskJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(refundTaskStep())
                .build();
    }

    @Bean
    public Step refundTaskStep() {
        return new StepBuilder("refundTaskStep", jobRepository)
                .<RefundTask, RefundTask>chunk(1)
                .reader(refundTaskReader())
                .processor(refundTaskProcessor())
                .writer(refundTaskWriter())
                .build();
    }

    /**
     * PENDING + 재시도 시각이 지난 RETRY_PENDING RefundTask만 읽어온다.
     */
    @Bean
    @StepScope
    public ListItemReader<RefundTask> refundTaskReader() {
        List<RefundTask> targets = new ArrayList<>();

        targets.addAll(refundTaskRepository.findByStatus(RefundTaskStatus.PENDING));
        targets.addAll(refundTaskRepository.findRetryPendingBefore(LocalDateTime.now()));

        log.info("[JOB2] 처리 대상 환불 task: {}건 (PENDING + 재시도 대상)", targets.size());
        return new ListItemReader<>(targets);
    }

    /**
     * RefundTask 1건에 대해 PortOne 환불 API를 호출한다.
     * PortOne API 호출 실패 시 지수 백오프 재시도 흐름
     * 재시도 횟수는 failReason 에 기록된 "[재시도 N회차]" 문자열로 파악한다.
     */
    @Bean
    @StepScope
    public ItemProcessor<RefundTask, RefundTask> refundTaskProcessor() {
        return task -> {
            log.info("[JOB2] 환불 처리 시작. taskId={} orderId={}", task.getId(), task.getOrderId());

            try {
                refundFacade.processInstructorRefundTask(task.getOrderId());
                task.markDone();
                log.info("[JOB2] 환불 완료. taskId={} orderId={}", task.getId(), task.getOrderId());

            } catch (Exception e) {
                // 현재까지 재시도 횟수 파악
                int retryCount = parseRetryCount(task.getFailReason());

                if (retryCount < MAX_RETRY_COUNT) {
                    // 지수 백오프: 1차=5분, 2차=10분, 3차=20분
                    long delayMinutes = INITIAL_RETRY_DELAY_MINUTES * (long) Math.pow(2, retryCount);
                    LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);

                    String reason = String.format("[재시도 %d회차] %s분 후 재시도 예정. 원인: %s",
                            retryCount + 1, delayMinutes, e.getMessage());
                    task.markRetryPending(nextRetryAt, reason);

                    log.warn("[JOB2] 환불 실패 — {}분 후 재시도. taskId={} orderId={} 재시도={}회차",
                            delayMinutes, task.getId(), task.getOrderId(), retryCount + 1);
                } else {
                    // MAX_RETRY_COUNT 초과 → 최종 실패
                    String reason = String.format("[최종 실패 - %d회 재시도 후 포기] %s",
                            MAX_RETRY_COUNT, e.getMessage());
                    task.markFailed(reason);

                    log.error("[JOB2] 환불 최종 실패 - 운영자 확인 필요. taskId={} orderId={}",
                            task.getId(), task.getOrderId(), e);
                }
            }

            return task;
        };
    }

    /**
     * 처리된 RefundTask 상태를 저장한다.
     */
    @Bean
    public ItemWriter<RefundTask> refundTaskWriter() {
        return chunk -> {
            refundTaskRepository.saveAll(chunk.getItems());
            log.info("[JOB2] Writer 완료. 처리 건수={}", chunk.size());
        };
    }

    /**
     * failReason 에서 현재까지 재시도 횟수를 파악한다.
     * "[재시도 N회차]" 패턴으로 기록되어 있으면 N을 반환, 없으면 0.
     */
    private int parseRetryCount(String failReason) {
        if (failReason == null) return 0;
        try {
            // "[재시도 N회차]" 에서 N 추출
            int start = failReason.indexOf("[재시도 ") + 5;
            int end = failReason.indexOf("회차]");
            if (start > 4 && end > start) {
                return Integer.parseInt(failReason.substring(start, end).trim());
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }
}
