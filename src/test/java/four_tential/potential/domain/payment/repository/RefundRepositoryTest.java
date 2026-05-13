package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.RefundReason;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.infra.redis.RedisTestContainer;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;
import four_tential.potential.presentation.payment.dto.RefundListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RefundRepositoryTest extends RedisTestContainer {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("sumRefundPriceByPaymentIdAndStatus 는 COMPLETED 환불 합계만 반환한다")
    void sumRefundPriceByPaymentIdAndStatus_returns_completed_sum() {
        UUID memberId = UUID.randomUUID();
        Order order = saveOrder(memberId, "소도구 필라테스 입문반", 3, 25000L);
        Payment payment = savePayment(order.getId(), memberId, "pg-sum-completed");
        saveCompletedRefund(payment, 25000L, 1, RefundReason.CANCEL, LocalDateTime.now().minusDays(2));
        saveCompletedRefund(payment, 50000L, 2, RefundReason.INSTRUCTOR, LocalDateTime.now().minusDays(1));
        saveFailedRefund(payment, 10000L, 1, RefundReason.CANCEL);

        Long result = refundRepository.sumRefundPriceByPaymentIdAndStatus(payment.getId(), RefundStatus.COMPLETED);

        assertThat(result).isEqualTo(75000L);
    }

    @Test
    @DisplayName("sumRefundPriceByPaymentIdAndStatus 는 환불 이력이 없으면 0을 반환한다")
    void sumRefundPriceByPaymentIdAndStatus_returns_zero_when_no_refund() {
        UUID memberId = UUID.randomUUID();
        Order order = saveOrder(memberId, "테스트 강좌", 1, 50000L);
        Payment payment = savePayment(order.getId(), memberId, "pg-sum-empty");

        Long result = refundRepository.sumRefundPriceByPaymentIdAndStatus(payment.getId(), RefundStatus.COMPLETED);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId 는 본인 환불이면 상세 DTO 를 반환한다")
    void findDetailByIdAndMemberId_returns_detail_for_owner() {
        UUID memberId = UUID.randomUUID();
        Order order = saveOrder(memberId, "소도구 필라테스 입문반", 2, 25000L);
        Payment payment = savePayment(order.getId(), memberId, "pg-refund-detail");
        Refund refund = saveCompletedRefund(
                payment, 50000L, 2, RefundReason.CANCEL,
                LocalDateTime.of(2026, 4, 21, 10, 0)
        );

        Optional<RefundDetailResponse> result =
                refundRepository.findDetailByIdAndMemberId(refund.getId(), memberId);

        assertThat(result).isPresent();
        RefundDetailResponse dto = result.get();
        assertThat(dto.refundId()).isEqualTo(refund.getId());
        assertThat(dto.paymentId()).isEqualTo(payment.getId());
        assertThat(dto.courseTitle()).isEqualTo("소도구 필라테스 입문반");
        assertThat(dto.cancelCount()).isEqualTo(2);
        assertThat(dto.refundPrice()).isEqualTo(50000L);
        assertThat(dto.reason()).isEqualTo(RefundReason.CANCEL);
        assertThat(dto.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(dto.refundedAt()).isEqualTo(LocalDateTime.of(2026, 4, 21, 10, 0));
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId 는 타인의 환불이면 빈 Optional 을 반환한다")
    void findDetailByIdAndMemberId_returns_empty_for_other_member() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Order order = saveOrder(ownerId, "소도구 필라테스 입문반", 1, 50000L);
        Payment payment = savePayment(order.getId(), ownerId, "pg-refund-other");
        Refund refund = saveCompletedRefund(payment, 50000L, 1, RefundReason.CANCEL, LocalDateTime.now());

        Optional<RefundDetailResponse> result =
                refundRepository.findDetailByIdAndMemberId(refund.getId(), otherId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus 는 status 가 null 이면 해당 회원의 전체 환불 목록을 반환한다")
    void findListByMemberIdAndStatus_returns_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Order order1 = saveOrder(memberId, "강좌A", 1, 50000L);
        Order order2 = saveOrder(memberId, "강좌B", 2, 25000L);
        Order otherOrder = saveOrder(otherId, "강좌C", 1, 70000L);
        Payment payment1 = savePayment(order1.getId(), memberId, "pg-list-all-1");
        Payment payment2 = savePayment(order2.getId(), memberId, "pg-list-all-2");
        Payment otherPayment = savePayment(otherOrder.getId(), otherId, "pg-list-all-3");
        saveCompletedRefund(payment1, 50000L, 1, RefundReason.CANCEL, LocalDateTime.of(2026, 4, 21, 9, 0));
        saveFailedRefund(payment2, 25000L, 1, RefundReason.CANCEL);
        saveCompletedRefund(otherPayment, 70000L, 1, RefundReason.INSTRUCTOR, LocalDateTime.of(2026, 4, 21, 8, 0));

        Page<RefundListResponse> result =
                refundRepository.findListByMemberIdAndStatus(memberId, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(RefundListResponse::courseTitle)
                .containsExactly("강좌A", "강좌B");
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus 는 status 조건으로 환불 목록을 필터링한다")
    void findListByMemberIdAndStatus_filters_by_status() {
        UUID memberId = UUID.randomUUID();
        Order completedOrder = saveOrder(memberId, "완료 강좌", 1, 50000L);
        Order failedOrder = saveOrder(memberId, "실패 강좌", 1, 40000L);
        Payment completedPayment = savePayment(completedOrder.getId(), memberId, "pg-list-filter-1");
        Payment failedPayment = savePayment(failedOrder.getId(), memberId, "pg-list-filter-2");
        saveCompletedRefund(completedPayment, 50000L, 1, RefundReason.CANCEL, LocalDateTime.now());
        saveFailedRefund(failedPayment, 40000L, 1, RefundReason.CANCEL);

        Page<RefundListResponse> result = refundRepository.findListByMemberIdAndStatus(
                memberId, RefundStatus.COMPLETED, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).courseTitle()).isEqualTo("완료 강좌");
        assertThat(result.getContent().get(0).status()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus 는 페이지네이션이 정상 동작한다")
    void findListByMemberIdAndStatus_pagination_works() {
        UUID memberId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            Order order = saveOrder(memberId, "강좌" + i, 1, 30000L + i);
            Payment payment = savePayment(order.getId(), memberId, "pg-list-page-" + i);
            saveCompletedRefund(
                    payment, 30000L + i, 1, RefundReason.CANCEL,
                    LocalDateTime.of(2026, 4, 21, 12, 0).minusHours(i)
            );
        }

        Page<RefundListResponse> firstPage =
                refundRepository.findListByMemberIdAndStatus(memberId, null, PageRequest.of(0, 2));
        Page<RefundListResponse> secondPage =
                refundRepository.findListByMemberIdAndStatus(memberId, null, PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    private Order saveOrder(UUID memberId, String title, int orderCount, long unitPrice) {
        Order order = Order.register(
                memberId,
                UUID.randomUUID(),
                orderCount,
                BigInteger.valueOf(unitPrice),
                title
        );
        order.completePayment();
        return orderRepository.saveAndFlush(order);
    }

    private Payment savePayment(UUID orderId, UUID memberId, String pgKey) {
        Payment payment = Payment.createPending(
                orderId,
                memberId,
                pgKey,
                100000L,
                100000L,
                PaymentPayWay.CARD
        );
        payment.confirmPaid();
        return paymentRepository.saveAndFlush(payment);
    }

    private Refund saveCompletedRefund(
            Payment payment,
            Long refundPrice,
            int cancelCount,
            RefundReason reason,
            LocalDateTime refundedAt
    ) {
        Refund refund = Refund.completed(payment, refundPrice, cancelCount, reason);
        ReflectionTestUtils.setField(refund, "refundedAt", refundedAt);
        return refundRepository.saveAndFlush(refund);
    }

    private Refund saveFailedRefund(Payment payment, Long refundPrice, int cancelCount, RefundReason reason) {
        Refund refund = Refund.failed(payment, refundPrice, cancelCount, reason);
        return refundRepository.saveAndFlush(refund);
    }
}
