package four_tential.potential.infra.batch.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.domain.payment.repository.RefundOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 강사 코스 취소 일괄 환불 Batch Job 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InstructorRefundJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RefundOutboxRepository refundOutboxRepository;
    private final RefundFacade refundFacade;

    // Todo: Job 작성
    /**
     * Job 이름: "instructorRefundJob"
     * RunIdIncrementer: 매 실행마다 run.id 를 자동 증가시켜
     * 이미 완료된 JobInstance 와 구분되는 새 JobInstance 를 만든다.
     */

    // Todo: Step 작성

    // Todo: ItemReader 작성

    // Todo: ItemProcessor 작성

    // Todo: ItemWriter 작성


}
