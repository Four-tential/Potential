package four_tential.potential.infra.batch.payment;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.payment.entity.CourseCancelOutbox;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import four_tential.potential.domain.payment.repository.CourseCancelOutboxRepository;
import four_tential.potential.domain.payment.repository.PaymentRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseCancelJobConfigTest {

    @InjectMocks
    private CourseCancelJobConfig courseCancelJobConfig;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CourseCancelOutboxRepository courseCancelOutboxRepository;

    @Mock
    private RefundTaskRepository refundTaskRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CourseCancelJobCompletionListener courseCancelJobCompletionListener;

    @Test
    @DisplayName("courseCancelJob bean을 생성한다")
    void courseCancelJob_creates_job() {
        Job job = courseCancelJobConfig.courseCancelJob();

        assertThat(job.getName()).isEqualTo("courseCancelJob");
    }

    @Test
    @DisplayName("courseCancelStep bean을 생성한다")
    void courseCancelStep_creates_step() {
        Step step = courseCancelJobConfig.courseCancelStep();

        assertThat(step.getName()).isEqualTo("courseCancelStep");
    }

    @Test
    @DisplayName("reader는 PENDING course_cancel_outbox만 읽는다")
    void courseCancelOutboxReader_reads_pending_outboxes() throws Exception {
        CourseCancelOutbox first = CourseCancelOutbox.pending(UUID.randomUUID());
        CourseCancelOutbox second = CourseCancelOutbox.pending(UUID.randomUUID());
        given(courseCancelOutboxRepository.findByStatus(CourseCancelOutboxStatus.PENDING))
                .willReturn(List.of(first, second));

        var reader = courseCancelJobConfig.courseCancelOutboxReader();

        assertThat(reader.read()).isEqualTo(first);
        assertThat(reader.read()).isEqualTo(second);
        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("processor는 REFUND_PENDING 주문과 payment가 있으면 refund_task를 생성한다")
    void courseCancelProcessor_creates_refund_tasks() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(courseId);
        Order order = createOrder(orderId, memberId, courseId, OrderStatus.REFUND_PENDING);
        Payment payment = createPayment(paymentId, orderId, memberId);

        given(orderRepository.findByCourseIdAndStatus(courseId, OrderStatus.REFUND_PENDING))
                .willReturn(List.of(order));
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));

        var result = courseCancelJobConfig.courseCancelProcessor().process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.DONE);
        assertThat(result.tasks()).hasSize(1);
        RefundTask task = result.tasks().get(0);
        assertThat(task.getCourseId()).isEqualTo(courseId);
        assertThat(task.getOrderId()).isEqualTo(orderId);
        assertThat(task.getPaymentId()).isEqualTo(paymentId);
        assertThat(task.getMemberId()).isEqualTo(memberId);
        assertThat(task.getStatus()).isEqualTo(RefundTaskStatus.PENDING);
    }

    @Test
    @DisplayName("processor는 REFUND_PENDING 주문이 없으면 outbox를 DONE 처리하고 task를 만들지 않는다")
    void courseCancelProcessor_marks_done_when_no_refund_pending_orders() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(courseId);
        given(orderRepository.findByCourseIdAndStatus(courseId, OrderStatus.REFUND_PENDING))
                .willReturn(List.of());

        var result = courseCancelJobConfig.courseCancelProcessor().process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.DONE);
        assertThat(result.tasks()).isEmpty();
    }

    @Test
    @DisplayName("processor는 payment가 없는 주문은 건너뛴다")
    void courseCancelProcessor_skips_order_when_payment_missing() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(courseId);
        Order order = createOrder(orderId, memberId, courseId, OrderStatus.REFUND_PENDING);

        given(orderRepository.findByCourseIdAndStatus(courseId, OrderStatus.REFUND_PENDING))
                .willReturn(List.of(order));
        given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

        var result = courseCancelJobConfig.courseCancelProcessor().process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.DONE);
        assertThat(result.tasks()).isEmpty();
    }

    @Test
    @DisplayName("processor는 예외가 나면 outbox를 FAILED 처리한다")
    void courseCancelProcessor_marks_failed_when_exception_occurs() throws Exception {
        UUID courseId = UUID.randomUUID();
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(courseId);
        given(orderRepository.findByCourseIdAndStatus(courseId, OrderStatus.REFUND_PENDING))
                .willThrow(new RuntimeException("db fail"));

        var result = courseCancelJobConfig.courseCancelProcessor().process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.FAILED);
        assertThat(outbox.getFailReason()).contains("db fail");
        assertThat(result.tasks()).isEmpty();
    }

    @Test
    @DisplayName("writer는 refund_task와 outbox를 함께 저장한다")
    void courseCancelWriter_saves_tasks_and_outbox() throws Exception {
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(UUID.randomUUID());
        RefundTask firstTask = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RefundTask secondTask = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var result = new CourseCancelJobConfig.CourseCancelJobResult(outbox, List.of(firstTask, secondTask));

        courseCancelJobConfig.courseCancelWriter().write(new Chunk<>(List.of(result)));

        verify(refundTaskRepository).saveAll(result.tasks());
        verify(courseCancelOutboxRepository).save(outbox);
    }

    @Test
    @DisplayName("writer는 task가 없으면 outbox만 저장한다")
    void courseCancelWriter_saves_only_outbox_when_no_tasks() throws Exception {
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(UUID.randomUUID());
        var result = new CourseCancelJobConfig.CourseCancelJobResult(outbox, List.of());

        courseCancelJobConfig.courseCancelWriter().write(new Chunk<>(List.of(result)));

        verify(refundTaskRepository, never()).saveAll(any());
        verify(courseCancelOutboxRepository).save(outbox);
    }

    private Order createOrder(UUID orderId, UUID memberId, UUID courseId, OrderStatus status) {
        Order order = Order.register(memberId, courseId, 1, BigInteger.valueOf(10000L), "Test Course");
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private Payment createPayment(UUID paymentId, UUID orderId, UUID memberId) {
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                "pg-" + paymentId,
                10000L,
                10000L,
                PaymentPayWay.CARD
        );
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }
}
