package four_tential.potential.infra.batch.order;

import four_tential.potential.application.order.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationBatchConfigTest {

    @InjectMocks
    private OrderConfirmationBatchConfig config;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private OrderService orderService;

    @Test
    @DisplayName("orderConfirmationJob bean을 생성한다")
    void orderConfirmationJob_creates_job() {
        Job job = config.orderConfirmationJob();

        assertThat(job.getName()).isEqualTo("orderConfirmationJob");
    }

    @Test
    @DisplayName("orderConfirmationStep bean을 생성한다")
    void orderConfirmationStep_creates_step() {
        Step step = config.orderConfirmationStep();

        assertThat(step.getName()).isEqualTo("orderConfirmationStep");
    }

    @Test
    @DisplayName("reader를 정상적으로 생성한다")
    void orderConfirmationReader_creates_reader() {
        JpaCursorItemReader<UUID> reader = config.orderConfirmationReader(null);

        assertThat(reader.getName()).isEqualTo("orderConfirmationReader");
    }

    @Test
    @DisplayName("processor는 환불 처리 성공 시 orderId를 반환한다")
    void orderConfirmationProcessor_returns_orderId_when_success() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderService.confirmOrderInNewTransaction(orderId)).willReturn(true);

        var result = config.orderConfirmationProcessor().process(orderId);

        verify(orderService).confirmOrderInNewTransaction(orderId);
        assertThat(result).isEqualTo(orderId);
    }

    @Test
    @DisplayName("processor는 환불 처리 실패 시 null을 반환한다")
    void orderConfirmationProcessor_returns_null_when_fails() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderService.confirmOrderInNewTransaction(orderId)).willReturn(false);

        var result = config.orderConfirmationProcessor().process(orderId);

        verify(orderService).confirmOrderInNewTransaction(orderId);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("writer는 에러 없이 청크를 처리한다")
    void orderConfirmationWriter_writes_chunk() throws Exception {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        assertThatCode(() ->
                config.orderConfirmationWriter().write(new Chunk<>(List.of(orderId1, orderId2)))
        ).doesNotThrowAnyException();
    }
}
