package four_tential.potential.infra.batch.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationJobSchedulerTest {

    @Mock
    private JobOperator jobOperator;

    @Mock
    private Job orderConfirmationJob;

    @InjectMocks
    private OrderConfirmationJobScheduler scheduler;

    @Test
    @DisplayName("결제 확정 스케줄러 실행 시 JobOperator를 통해 Job을 실행한다")
    void confirmOrders_success() throws Exception {
        // when
        scheduler.confirmOrders();

        // then
        verify(jobOperator).start(eq(orderConfirmationJob), any(JobParameters.class));
    }
}
