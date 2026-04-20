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
    @DisplayName("findByPgKey 호출 시 pgKey 가 일치하는 결제를 조회한다")
    void findByPgKey_returns_payment() {
        Payment payment = createPayment(UUID.randomUUID(), "pg-key-1");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByPgKey("pg-key-1");

        assertThat(result).isPresent();
        assertThat(result.get().getPgKey()).isEqualTo("pg-key-1");
    }

    @Test
    @DisplayName("findByPgKey 호출 시 pgKey 가 없으면 빈 Optional 을 반환한다")
    void findByPgKey_returns_empty_when_not_found() {
        Optional<Payment> result = paymentRepository.findByPgKey("missing-pg-key");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByOrderId 호출 시 orderId 가 일치하는 결제를 조회한다")
    void findByOrderId_returns_payment() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "pg-key-by-order");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByOrderId(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("findByPgKeyForUpdate 호출 시 pgKey 가 일치하는 결제를 조회한다")
    void findByPgKeyForUpdate_returns_payment() {
        Payment payment = createPayment(UUID.randomUUID(), "pg-key-lock");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> result = paymentRepository.findByPgKeyForUpdate("pg-key-lock");

        assertThat(result).isPresent();
        assertThat(result.get().getPgKey()).isEqualTo("pg-key-lock");
    }

    @Test
    @DisplayName("existsByOrderId 호출 시 같은 주문의 결제가 있으면 true 를 반환한다")
    void existsByOrderId_returns_true_when_exists() {
        UUID orderId = UUID.randomUUID();
        Payment payment = createPayment(orderId, "pg-key-order");
        paymentRepository.saveAndFlush(payment);

        boolean result = paymentRepository.existsByOrderId(orderId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByOrderId 호출 시 같은 주문의 결제가 없으면 false 를 반환한다")
    void existsByOrderId_returns_false_when_not_exists() {
        boolean result = paymentRepository.existsByOrderId(UUID.randomUUID());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId — 본인 결제이면 courseTitle, orderCount 를 포함한 DTO 를 반환한다")
    void findDetailByIdAndMemberId_returns_dto_with_course_info() {
        UUID memberId = UUID.randomUUID();
        Order order   = saveOrder(memberId);
        Payment payment = savePayment(order.getId(), memberId, "pg-detail-1");

        Optional<PaymentDetailResponse> result =
                paymentRepository.findDetailByIdAndMemberId(payment.getId(), memberId);

        assertThat(result).isPresent();
        PaymentDetailResponse dto = result.get();
        assertThat(dto.paymentId()).isEqualTo(payment.getId());
        assertThat(dto.orderId()).isEqualTo(order.getId());
        assertThat(dto.courseTitle()).isEqualTo("테스트 강좌");  // Order.titleSnap
        assertThat(dto.orderCount()).isEqualTo(2);              // Order.orderCount
        assertThat(dto.paidTotalPrice()).isEqualTo(125000L);
        assertThat(dto.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId — 타인의 결제이면 빈 Optional 을 반환한다")
    void findDetailByIdAndMemberId_returns_empty_for_other_member() {
        UUID owner  = UUID.randomUUID();
        UUID other  = UUID.randomUUID();
        Order order = saveOrder(owner);
        Payment payment = savePayment(order.getId(), owner, "pg-detail-2");

        Optional<PaymentDetailResponse> result =
                paymentRepository.findDetailByIdAndMemberId(payment.getId(), other);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findDetailByIdAndMemberId — 존재하지 않는 paymentId 이면 빈 Optional 을 반환한다")
    void findDetailByIdAndMemberId_returns_empty_when_not_exists() {
        Optional<PaymentDetailResponse> result =
                paymentRepository.findDetailByIdAndMemberId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus — status 가 null 이면 해당 회원의 전체 결제를 반환한다")
    void findListByMemberIdAndStatus_returns_all_when_status_null() {
        UUID memberId = UUID.randomUUID();
        Order order1 = saveOrder(memberId);
        Order order2 = saveOrder(memberId);
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
    @DisplayName("findListByMemberIdAndStatus — status 가 PAID 이면 PAID 상태만 반환한다")
    void findListByMemberIdAndStatus_filters_by_status() {
        UUID memberId = UUID.randomUUID();
        Order order1  = saveOrder(memberId);
        Order order2  = saveOrder(memberId);
        savePayment(order1.getId(), memberId, "pg-filter-pending");   // PENDING
        Payment paid = savePayment(order2.getId(), memberId, "pg-filter-paid");
        paid.confirmPaid();
        paymentRepository.saveAndFlush(paid);

        Page<PaymentListResponse> result = paymentRepository.findListByMemberIdAndStatus(
                memberId, PaymentStatus.PAID, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus — 타인의 결제는 조회되지 않는다")
    void findListByMemberIdAndStatus_excludes_other_members() {
        UUID myId    = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Order myOrder    = saveOrder(myId);
        Order otherOrder = saveOrder(otherId);
        savePayment(myOrder.getId(),    myId,    "pg-mine");
        savePayment(otherOrder.getId(), otherId, "pg-other");

        Page<PaymentListResponse> result = paymentRepository.findListByMemberIdAndStatus(
                myId, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus — 결제가 없으면 빈 페이지를 반환한다")
    void findListByMemberIdAndStatus_returns_empty_page() {
        Page<PaymentListResponse> result = paymentRepository.findListByMemberIdAndStatus(
                UUID.randomUUID(), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("findListByMemberIdAndStatus — 페이지네이션이 정상 동작한다")
    void findListByMemberIdAndStatus_pagination_works() {
        UUID memberId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            Order order = saveOrder(memberId);
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

    @Test
    @DisplayName("findListByMemberIdAndStatus — courseTitle 과 orderCount 가 포함되어 반환된다")
    void findListByMemberIdAndStatus_includes_course_info() {
        UUID memberId = UUID.randomUUID();
        Order order   = saveOrder(memberId);
        savePayment(order.getId(), memberId, "pg-course-info");

        Page<PaymentListResponse> result = paymentRepository.findListByMemberIdAndStatus(
                memberId, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        PaymentListResponse dto = result.getContent().get(0);
        assertThat(dto.courseTitle()).isEqualTo("테스트 강좌");
        assertThat(dto.orderCount()).isEqualTo(2);
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

    private Order saveOrder(UUID memberId) {
        Order order = Order.register(
                memberId,
                UUID.randomUUID(),
                2,
                BigInteger.valueOf(62500),
                "테스트 강좌"
        );
        return orderRepository.saveAndFlush(order);
    }

    private Payment savePayment(UUID orderId, UUID memberId, String pgKey) {
        Payment payment = Payment.createPending(
                orderId, memberId,
                pgKey, 125000L, 125000L, PaymentPayWay.CARD
        );
        return paymentRepository.saveAndFlush(payment);
    }

}
