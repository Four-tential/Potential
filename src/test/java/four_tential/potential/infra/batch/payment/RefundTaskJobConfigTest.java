package four_tential.potential.infra.batch.payment;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import four_tential.potential.domain.payment.repository.RefundTaskRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundTaskJobConfigTest {

    @InjectMocks
    private RefundTaskJobConfig refundTaskJobConfig;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RefundTaskRepository refundTaskRepository;

    @Mock
    private RefundFacade refundFacade;

    @Test
    @DisplayName("refundTaskJob bean을 생성한다")
    void refundTaskJob_creates_job() {
        Job job = refundTaskJobConfig.refundTaskJob();

        assertThat(job.getName()).isEqualTo("refundTaskJob");
    }

    @Test
    @DisplayName("refundTaskStep bean을 생성한다")
    void refundTaskStep_creates_step() {
        Step step = refundTaskJobConfig.refundTaskStep();

        assertThat(step.getName()).isEqualTo("refundTaskStep");
    }

    @Test
    @DisplayName("reader는 PENDING refund_task만 읽는다")
    void refundTaskReader_reads_pending_tasks() throws Exception {
        RefundTask first = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RefundTask second = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RefundTask retryPending = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        retryPending.markRetryPending(LocalDateTime.now().minusMinutes(1), "retry later");
        given(refundTaskRepository.findByStatus(RefundTaskStatus.PENDING)).willReturn(List.of(first, second));
        given(refundTaskRepository.findRetryPendingBefore(any(LocalDateTime.class))).willReturn(List.of(retryPending));

        var reader = refundTaskJobConfig.refundTaskReader();

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isEqualTo(retryPending);
        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("processor는 환불 성공 시 task를 DONE 처리한다")
    void refundTaskProcessor_marks_done_when_refund_succeeds() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());

        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        verify(refundFacade).processInstructorRefundTask(orderId);
        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.DONE);
        assertThat(result.getFailReason()).isNull();
    }

    @Test
    @DisplayName("processor는 환불 실패 시 task를 FAILED 처리한다")
    void refundTaskProcessor_marks_failed_when_refund_fails() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());
        doThrow(new RuntimeException("refund fail"))
                .when(refundFacade).processInstructorRefundTask(orderId);

        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        verify(refundFacade).processInstructorRefundTask(orderId);
        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.RETRY_PENDING);
        assertThat(result.getNextRetryAt()).isNotNull();
        assertThat(result.getFailReason()).contains("refund fail");
    }

    @Test
    @DisplayName("processor는 최대 재시도 후에 FAILED 처리한다")
    void refundTaskProcessor_marks_failed_after_max_retries() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());
        doThrow(new RuntimeException("refund fail"))
                .when(refundFacade).processInstructorRefundTask(orderId);

        var processor = refundTaskJobConfig.refundTaskProcessor();

        LocalDateTime t1 = LocalDateTime.now();
        var firstRetry = processor.process(task);
        assertThat(firstRetry.getStatus()).isEqualTo(RefundTaskStatus.RETRY_PENDING);
        assertThat(firstRetry.getNextRetryAt())
                .isAfterOrEqualTo(t1.plusMinutes(5).minusSeconds(10))
                .isBeforeOrEqualTo(t1.plusMinutes(5).plusSeconds(10));
        LocalDateTime t2 = LocalDateTime.now();
        var secondRetry = processor.process(task);
        assertThat(secondRetry.getStatus()).isEqualTo(RefundTaskStatus.RETRY_PENDING);
        assertThat(secondRetry.getNextRetryAt())
                .isAfterOrEqualTo(t2.plusMinutes(10).minusSeconds(10))
                .isBeforeOrEqualTo(t2.plusMinutes(10).plusSeconds(10));
        LocalDateTime t3 = LocalDateTime.now();
        var thirdRetry = processor.process(task);
        assertThat(thirdRetry.getStatus()).isEqualTo(RefundTaskStatus.RETRY_PENDING);
        assertThat(thirdRetry.getNextRetryAt())
                .isAfterOrEqualTo(t3.plusMinutes(20).minusSeconds(10))
                .isBeforeOrEqualTo(t3.plusMinutes(20).plusSeconds(10));
        var result = processor.process(task);

        verify(refundFacade, times(4)).processInstructorRefundTask(orderId);
        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.FAILED);
        assertThat(result.getNextRetryAt()).isNull();
        assertThat(result.getFailReason()).contains("refund fail");
    }

    @Test
    @DisplayName("writer는 처리된 refund_task를 저장한다")
    void refundTaskWriter_saves_tasks() throws Exception {
        RefundTask first = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RefundTask second = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        refundTaskJobConfig.refundTaskWriter().write(new Chunk<>(List.of(first, second)));

        verify(refundTaskRepository).saveAll(eq(List.of(first, second)));
    }

    @Test
    @DisplayName("processor는 이미 전액 환불된 task는 재시도하지 않고 FAILED 처리한다")
    void refundTaskProcessor_marks_failed_immediately_when_already_fully_refunded() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());

        doThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_ALREADY_FULLY_REFUNDED))
                .when(refundFacade).processInstructorRefundTask(orderId);

        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.FAILED);
        assertThat(result.getNextRetryAt()).isNull();
        assertThat(result.getFailReason()).contains("비재시도 실패");
    }

    @Test
    @DisplayName("processor는 주문 상태 오류를 재시도하지 않고 FAILED 처리한다")
    void refundTaskProcessor_marks_failed_immediately_when_order_status_invalid() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());

        doThrow(new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS))
                .when(refundFacade).processInstructorRefundTask(orderId);

        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.FAILED);
        assertThat(result.getNextRetryAt()).isNull();
        assertThat(result.getFailReason()).contains("비재시도 실패");
    }

    @Test
    @DisplayName("processor는 재시도 문구를 해석할 수 없으면 1회차 재시도로 처리한다")
    void refundTaskProcessor_uses_first_retry_when_fail_reason_is_unparseable() throws Exception {
        UUID orderId = UUID.randomUUID();
        RefundTask task = RefundTask.pending(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID());
        task.markFailed("문구가 바뀌어서 재시도 횟수를 읽을 수 없음");

        doThrow(new RuntimeException("refund fail"))
                .when(refundFacade).processInstructorRefundTask(orderId);

        LocalDateTime now = LocalDateTime.now();
        var result = refundTaskJobConfig.refundTaskProcessor().process(task);

        assertThat(result.getStatus()).isEqualTo(RefundTaskStatus.RETRY_PENDING);
        assertThat(result.getNextRetryAt())
                .isAfterOrEqualTo(now.plusMinutes(5).minusSeconds(10))
                .isBeforeOrEqualTo(now.plusMinutes(5).plusSeconds(10));
    }

}
