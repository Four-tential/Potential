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

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RefundTaskJobConfig {

    private final JobRepository jobRepository;
    private final RefundTaskRepository refundTaskRepository;
    private final RefundFacade refundFacade;

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
     * PENDING 상태 RefundTask만 읽어온다.
     */
    @Bean
    @StepScope
    public ListItemReader<RefundTask> refundTaskReader() {
        List<RefundTask> targets = refundTaskRepository.findByStatus(RefundTaskStatus.PENDING);
        log.info("[JOB2] 처리 대상 환불 task: {}건", targets.size());
        return new ListItemReader<>(targets);
    }

    /**
     * RefundTask 1건에 대해 PortOne 환불 API를 호출한다.
     *
     * [null 반환 안 함]
     *   항상 task를 반환. 예외 발생 시 FAILED로 마킹 후 반환.
     *   → 다른 주문 환불 처리는 계속 진행됨.
     */
    @Bean
    @StepScope
    public ItemProcessor<RefundTask, RefundTask> refundTaskProcessor() {
        return task -> {
            log.info("[JOB2] 환불 처리 시작. taskId={} orderId={}", task.getId(), task.getOrderId());

            try {
                refundFacade.processInstructorRefundTask(task.getOrderId());

                task.markDone();
                log.info("[JOB2] 환불 완료. taskId={} orderId={}",
                        task.getId(), task.getOrderId());

            } catch (Exception e) {
                task.markFailed(e.getMessage());
                log.error("[JOB2] 환불 실패. taskId={} orderId={}",
                        task.getId(), task.getOrderId(), e);
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
}
