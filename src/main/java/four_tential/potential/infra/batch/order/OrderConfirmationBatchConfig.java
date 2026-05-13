package four_tential.potential.infra.batch.order;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.domain.order.OrderStatus;
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
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderConfirmationBatchConfig {

    private final JobRepository jobRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final OrderService orderService;

    @Bean
    public Job orderConfirmationJob() {
        return new JobBuilder("orderConfirmationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(orderConfirmationStep())
                .build();
    }

    @Bean
    public Step orderConfirmationStep() {
        return new StepBuilder("orderConfirmationStep", jobRepository)
                .<UUID, UUID>chunk(100)
                .reader(orderConfirmationReader(null))
                .processor(orderConfirmationProcessor())
                .writer(orderConfirmationWriter())
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<UUID> orderConfirmationReader(
            @Value("#{jobParameters['now']}") String nowStr
    ) {
        LocalDateTime now = (nowStr != null) ? LocalDateTime.parse(nowStr) : LocalDateTime.now();
        LocalDateTime targetDate = now.plusDays(7);

        return new JpaCursorItemReaderBuilder<UUID>()
                .name("orderConfirmationReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT o.id FROM Order o, Course c WHERE o.courseId = c.id AND o.status = :status AND c.startAt < :targetDate")
                .parameterValues(Map.of(
                        "status", OrderStatus.PAID,
                        "targetDate", targetDate
                ))
                .build();
    }

    @Bean
    public ItemProcessor<UUID, UUID> orderConfirmationProcessor() {
        return orderId -> {
            boolean success = orderService.confirmOrderInNewTransaction(orderId);
            return success ? orderId : null;
        };
    }

    @Bean
    public ItemWriter<UUID> orderConfirmationWriter() {
        return chunk -> {
            if (!chunk.isEmpty()) {
                log.info("결제 완료 주문 자동 확정 청크 처리 완료 ({}건)", chunk.size());
            }
        };
    }
}
