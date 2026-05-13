package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.infra.redis.RedisTestContainer;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentRepositoryTest extends RedisTestContainer {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("findByPgKey는 일치하는 결제를 반환한다")
    void findByPgKey_returns_payment() {
        Payment payment = createPayment(UUID.randomUUID(), "pg-key-1");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByPgKey("pg-key-1");

        assertThat(result).isPresent();
        assertThat(result.get().getPgKey()).isEqualTo("pg-key-1");
    }

    @Test
    @DisplayName("findByPgKey는 결제가 없으면 빈 Optional을 반환한다")
    void findByPgKey_returns_empty_when_not_found() {
        assertThat(paymentRepository.findByPgKey("missing-pg-key")).isEmpty();
    }

    @Test
    @DisplayName("findByOrderId는 일치하는 결제를 반환한다")
    void findByOrderId_returns_payment() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "pg-key-by-order");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByOrderId(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("findByPgKeyForUpdate는 일치하는 결제를 반환한다")
    void findByPgKeyForUpdate_returns_payment() {
        Payment payment = createPayment(UUID.randomUUID(), "pg-key-lock");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByPgKeyForUpdate("pg-key-lock");

        assertThat(result).isPresent();
        assertThat(result.get().getPgKey()).isEqualTo("pg-key-lock");
    }

    @Test
    @DisplayName("existsByOrderId는 결제가 존재하면 true를 반환한다")
    void existsByOrderId_returns_true_when_exists() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "pg-key-order");
        paymentRepository.saveAndFlush(payment);

        assertThat(paymentRepository.existsByOrderId(orderId)).isTrue();
    }

    @Test
    @DisplayName("existsByOrderId는 결제가 없으면 false를 반환한다")
    void existsByOrderId_returns_false_when_not_exists() {
        assertThat(paymentRepository.existsByOrderId(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId는 본인 결제의 상세 projection을 반환한다")
    void findDetailByIdAndMemberId_returns_projection_for_owner() {
        UUID memberId = UUID.randomUUID();
        Order order = saveOrder(memberId, UUID.randomUUID(), 2, 62500L, "Test Course");
        Payment payment = savePayment(order.getId(), memberId, "pg-detail-1");

        Optional<PaymentDetailResponse> result =
                paymentRepository.findDetailByIdAndMemberId(payment.getId(), memberId);

        assertThat(result).isPresent();
        PaymentDetailResponse detail = result.get();
        assertThat(detail.paymentId()).isEqualTo(payment.getId());
        assertThat(detail.orderId()).isEqualTo(order.getId());
        assertThat(detail.courseTitle()).isEqualTo("Test Course");
        assertThat(detail.orderCount()).isEqualTo(2);
        assertThat(detail.paidTotalPrice()).isEqualTo(125000L);
        assertThat(detail.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId는 타인의 결제면 빈 Optional을 반환한다")
    void findDetailByIdAndMemberId_returns_empty_for_other_member() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Order order = saveOrder(ownerId, UUID.randomUUID(), 2, 62500L, "Test Course");
        Payment payment = savePayment(order.getId(), ownerId, "pg-detail-2");

        Optional<PaymentDetailResponse> result =
                paymentRepository.findDetailByIdAndMemberId(payment.getId(), otherId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findRefundPreviewData는 본인 결제의 환불 미리보기 projection을 반환한다")
    void findRefundPreviewData_returns_projection_for_owner() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = saveOrder(memberId, courseId, 2, 62500L, "Preview Course");
        Payment payment = savePayment(order.getId(), memberId, "pg-refund-preview");

        Optional<RefundPreviewData> result =
                paymentRepository.findRefundPreviewData(payment.getId(), memberId);

        assertThat(result).isPresent();
        RefundPreviewData data = result.get();
        assertThat(data.paymentId()).isEqualTo(payment.getId());
        assertThat(data.memberId()).isEqualTo(memberId);
        assertThat(data.paidTotalPrice()).isEqualTo(125000L);
        assertThat(data.paymentStatusName()).isEqualTo(PaymentStatus.PENDING.name());
        assertThat(data.courseId()).isEqualTo(courseId);
        assertThat(data.courseTitle()).isEqualTo("Preview Course");
        assertThat(data.currentOrderCount()).isEqualTo(2);
        assertThat(data.priceSnap()).isEqualTo(BigInteger.valueOf(62500L));
    }

    @Test
    @DisplayName("findRefundPreviewData는 타인의 결제면 빈 Optional을 반환한다")
    void findRefundPreviewData_returns_empty_for_other_member() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = saveOrder(ownerId, courseId, 2, 62500L, "Preview Course");
        Payment payment = savePayment(order.getId(), ownerId, "pg-refund-preview-other");

        Optional<RefundPreviewData> result =
                paymentRepository.findRefundPreviewData(payment.getId(), otherId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus는 status가 null이면 회원의 전체 결제 목록을 반환한다")
    void findListByMemberIdAndStatus_returns_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        Order order1 = saveOrder(memberId, UUID.randomUUID(), 2, 62500L, "Course A");
        Order order2 = saveOrder(memberId, UUID.randomUUID(), 1, 50000L, "Course B");
        savePayment(order1.getId(), memberId, "pg-list-1");
        Payment paid = savePayment(order2.getId(), memberId, "pg-list-2");
        paid.confirmPaid();
        paymentRepository.saveAndFlush(paid);

        Page<PaymentListResponse> result = paymentRepository.findListByMemberIdAndStatus(
                memberId, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus는 status 조건으로 결제 목록을 필터링한다")
    void findListByMemberIdAndStatus_filters_by_status() {
        UUID memberId = UUID.randomUUID();
        Order order1 = saveOrder(memberId, UUID.randomUUID(), 2, 62500L, "Course A");
        Order order2 = saveOrder(memberId, UUID.randomUUID(), 1, 50000L, "Course B");
        savePayment(order1.getId(), memberId, "pg-filter-pending");
        Payment paid = savePayment(order2.getId(), memberId, "pg-filter-paid");
        paid.confirmPaid();
        paymentRepository.saveAndFlush(paid);

        Page<PaymentListResponse> result = paymentRepository.findListByMemberIdAndStatus(
                memberId, PaymentStatus.PAID, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus는 페이지네이션이 정상 동작한다")
    void findListByMemberIdAndStatus_pagination_works() {
        UUID memberId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            Order order = saveOrder(memberId, UUID.randomUUID(), 1, 30000L + i, "Course " + i);
            savePayment(order.getId(), memberId, "pg-page-" + i);
        }

        Page<PaymentListResponse> firstPage = paymentRepository.findListByMemberIdAndStatus(
                memberId, null, PageRequest.of(0, 3));
        Page<PaymentListResponse> secondPage = paymentRepository.findListByMemberIdAndStatus(
                memberId, null, PageRequest.of(1, 3));

        assertThat(firstPage.getContent()).hasSize(3);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    private Payment createPayment(UUID orderId, String pgKey) {
        return Payment.createPending(
                orderId,
                UUID.randomUUID(),
                pgKey,
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
    }

    private Order saveOrder(UUID memberId, UUID courseId, int orderCount, long unitPrice, String title) {
        Order order = Order.register(
                memberId,
                courseId,
                orderCount,
                BigInteger.valueOf(unitPrice),
                title
        );
        return orderRepository.saveAndFlush(order);
    }

    private Payment savePayment(UUID orderId, UUID memberId, String pgKey) {
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                pgKey,
                125000L,
                125000L,
                PaymentPayWay.CARD
        );
        return paymentRepository.saveAndFlush(payment);
    }
}
