package four_tential.potential.infra.batch.payment;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.payment.entity.CourseCancelOutbox;
import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import four_tential.potential.domain.payment.repository.CourseCancelOutboxRepository;
import four_tential.potential.domain.payment.repository.PaymentRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CourseCancelJobConfig {

    private final JobRepository jobRepository;
    private final CourseCancelOutboxRepository courseCancelOutboxRepository;
    private final RefundTaskRepository refundTaskRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CourseCancelJobCompletionListener courseCancelJobCompletionListener;

    // Todo: Job
    @Bean
    public Job courseCancelJob() {
        return new JobBuilder("courseCancelJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(courseCancelJobCompletionListener)
                .start(courseCancelStep())
                .build();
    }

    // Todo: Step
    @Bean
    public Step courseCancelStep() {
        return new StepBuilder("courseCancelStep", jobRepository)
                .<CourseCancelOutbox, CourseCancelJobResult>chunk(1)
                .reader(courseCancelOutboxReader())
                .processor(courseCancelProcessor())
                .writer(courseCancelWriter())
                .build();
    }

    // Todo Reader: PENDING 상태 CourseCancelOutbox를 읽어온다
    @Bean
    @StepScope
    public ListItemReader<CourseCancelOutbox> courseCancelOutboxReader() {
        List<CourseCancelOutbox> pending = courseCancelOutboxRepository.findByStatus(CourseCancelOutboxStatus.PENDING);
        log.info("[JOB1] 처리 대상 코스: {}건", pending.size());
        return new ListItemReader<>(pending);
    }

    // Todo: Processor
    /**
     * 코스 1개(outbox 1건)에 대해 REFUND_PENDING 주문을 조회하고
     * RefundTask 목록을 만든다.
     *
     * [반환 타입 CourseCancelJobResult]
     * outbox와 생성된 RefundTask 목록을 함께 담아서 Writer로 전달한다.
     * outbox 상태 변경과 RefundTask INSERT를 같은 Writer 트랜잭션에서 처리하기 위해서다.
     */
    @Bean
    @StepScope
    public ItemProcessor<CourseCancelOutbox, CourseCancelJobResult> courseCancelProcessor() {
        return outbox -> {
            UUID courseId = outbox.getCourseId();
            log.info("[JOB1] 코스 처리 시작. courseId={}", courseId);

            try {
                List<Order> refundPendingOrders = orderRepository
                        .findByCourseIdAndStatus(courseId, OrderStatus.REFUND_PENDING);

                if (refundPendingOrders.isEmpty()) {
                    log.info("[JOB1] REFUND_PENDING 주문 없음. courseId={}", courseId);
                    outbox.markDone();
                    return new CourseCancelJobResult(outbox, List.of());
                }

                List<RefundTask> tasks = new ArrayList<>();
                for (Order order : refundPendingOrders) {
                    // payment_id를 얻기 위해 payment 조회
                    // pg_key, refund_amount는 Job2가 처리 시점에 조회
                    var payment = paymentRepository.findByOrderId(order.getId())
                            .orElse(null);
                    if (payment == null) {
                        log.warn("[JOB1] payment 없음. orderId={}", order.getId());
                        continue;
                    }

                    tasks.add(RefundTask.pending(
                            courseId,
                            order.getId(),
                            payment.getId(),
                            order.getMemberId()
                    ));
                }

                outbox.markDone();
                log.info("[JOB1] refund_task 생성 완료. courseId={} tasks={}건",
                        courseId, tasks.size());

                return new CourseCancelJobResult(outbox, tasks);

            } catch (Exception e) {
                outbox.markFailed(e.getMessage());
                log.error("[JOB1] 처리 중 예외 발생. courseId={}", courseId, e);
                return new CourseCancelJobResult(outbox, List.of());
            }
        };
    }

    // Todo Writer: RefundTask를 INSERT하고 CourseCancelOutbox 상태를 저장한다.
    @Bean
    public ItemWriter<CourseCancelJobResult> courseCancelWriter() {
        return chunk -> {
            for (CourseCancelJobResult result : chunk.getItems()) {
                if (!result.tasks().isEmpty()) {
                    refundTaskRepository.saveAll(result.tasks());
                }
                courseCancelOutboxRepository.save(result.outbox());
            }
            log.info("[JOB1] Writer 완료. 처리 건수={}", chunk.size());
        };
    }

    /**
     * Processor → Writer 전달용 record
     */
    record CourseCancelJobResult(
            CourseCancelOutbox outbox,
            List<RefundTask> tasks
    ) {
    }
}
