package four_tential.potential.domain.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 시 초기 상태는 PENDING이며 만료 시간은 10분 후로 설정된다")
    void registerOrderSuccess() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        BigInteger price = BigInteger.valueOf(10000);
        LocalDateTime now = LocalDateTime.now();

        // when
        Order order = Order.register(memberId, courseId, 1, price, "테스트 코스");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        // 실행 시간을 고려하여 1초 이내의 오차만 허용하고, 정확히 10분 후인지 검증
        assertThat(order.getExpireAt()).isCloseTo(now.plusMinutes(10), within(1, ChronoUnit.SECONDS));
        assertThat(order.getTotalPriceSnap()).isEqualTo(price);
    }

    @Test
    @DisplayName("PENDING 상태의 주문은 결제 완료(PAID) 처리할 수 있다")
    void completePaymentSuccess() {
        // given
        Order order = createPendingOrder();

        // when
        order.completePayment();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 주문을 결제 완료 처리하면 예외가 발생한다")
    void completePaymentFail_InvalidStatus() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태로 변경

        // when & then
        assertThatThrownBy(order::completePayment)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getMessage());
    }

    @Test
    @DisplayName("만료 시간이 지난 주문을 결제 완료 처리하면 EXPIRED 상태로 변경되고 예외가 발생한다")
    void completePaymentFail_Expired() {
        // given
        Order order = createPendingOrder();
        // 리플렉션을 사용하여 만료 시간을 과거로 설정
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));

        // when & then
        assertThatThrownBy(order::completePayment)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_ORDER_EXPIRED.getMessage());
        
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("PENDING 상태의 주문은 만료(EXPIRED) 처리할 수 있다")
    void expireSuccess() {
        // given
        Order order = createPendingOrder();

        // when
        order.expire();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 주문을 만료 처리하면 예외가 발생한다")
    void expireFail() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태

        // when & then
        assertThatThrownBy(order::expire)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getMessage())
                .hasFieldOrPropertyWithValue("httpStatus", OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getHttpStatus());
    }

    @Test
    @DisplayName("관리자는 주문 상태를 임의로 변경할 수 있다")
    void updateStatusByAdmin_Success() {
        // given
        Order order = createPendingOrder();

        // when
        order.updateStatusByAdmin(OrderStatus.PAID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("관리자가 주문을 취소 상태로 변경하면 취소 시각이 기록된다")
    void updateStatusByAdmin_Cancelled_At_Success() {
        // given
        Order order = createPendingOrder();
        LocalDateTime beforeUpdate = LocalDateTime.now();

        // when
        order.updateStatusByAdmin(OrderStatus.CANCELLED);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("이미 취소된 주문의 상태를 관리자가 다시 취소로 변경해도 취소 시각은 갱신되지 않는다")
    void updateStatusByAdmin_Already_Cancelled_Not_Update_Time() {
        // given
        Order order = createPendingOrder();
        order.updateStatusByAdmin(OrderStatus.CANCELLED);
        LocalDateTime firstCancelledAt = order.getCancelledAt();

        // when
        order.updateStatusByAdmin(OrderStatus.CANCELLED);

        // then
        assertThat(order.getCancelledAt()).isEqualTo(firstCancelledAt);
    }

    @Test
    @DisplayName("취소된 주문을 관리자가 다른 상태로 변경하면 취소 시각이 초기화된다")
    void updateStatusByAdmin_From_Cancelled_To_Other_Clear_Time() {
        // given
        Order order = createPendingOrder();
        order.updateStatusByAdmin(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();

        // when
        order.updateStatusByAdmin(OrderStatus.PAID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("PENDING 상태의 주문은 코스 시작 7일 전까지만 취소할 수 있다")
    void cancelPendingOrderSuccess() {
        // given
        Order order = createPendingOrder();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime courseStartAt = now.plusDays(8);

        // when
        order.cancel(courseStartAt, now);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("PENDING 상태의 주문이 코스 시작 7일 이내라면 취소 시 예외가 발생한다")
    void cancelPendingOrderFail_TooLate() {
        // given
        Order order = createPendingOrder();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime courseStartAt = now.plusDays(6);

        // when & then
        assertThatThrownBy(() -> order.cancel(courseStartAt, now))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_DATETIME.getMessage());
    }

    @Test
    @DisplayName("PAID 상태의 주문은 코스 시작 7일 전까지만 취소할 수 있다")
    void cancelPaidOrderSuccess() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime courseStartAt = now.plusDays(8);

        // when
        order.cancel(courseStartAt, now);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("PAID 상태의 주문이 코스 시작 7일 이내라면 취소 시 예외가 발생한다")
    void cancelPaidOrderFail_TooLate() {
        // given
        Order order = createPendingOrder();
        order.completePayment();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime courseStartAt = now.plusDays(6);

        // when & then
        assertThatThrownBy(() -> order.cancel(courseStartAt, now))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_DATETIME.getMessage());
    }

    @Test
    @DisplayName("코스 시작 정확히 7일 전인 시점에는 취소가 가능하다 (경계값 테스트)")
    void cancelOrderSuccess_ExactlySevenDaysBefore() {
        // given
        Order order = createPendingOrder();
        // 기준 시각 설정
        LocalDateTime now = LocalDateTime.now();
        // 정확히 7일 후로 설정
        LocalDateTime courseStartAt = now.plusDays(7);

        // when
        // now가 courseStartAt.minusDays(7)과 정확히 일치하므로 isAfter()는 false
        order.cancel(courseStartAt, now);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 만료된 주문은 취소할 수 없다")
    void cancelOrderFail_AlreadyExpired() {
        // given
        Order order = createPendingOrder();
        order.expire();
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> order.cancel(now.plusDays(10), now))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER.getMessage());
    }

    @Test
    @DisplayName("이미 취소된 주문은 다시 취소할 수 없다")
    void cancelOrderFail_AlreadyCancelled() {
        // given
        Order order = createPendingOrder();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime courseStartAt = now.plusDays(10);
        order.cancel(courseStartAt, now); // 첫 번째 취소

        // when & then
        assertThatThrownBy(() -> order.cancel(courseStartAt, now))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER.getMessage());
    }

    @Test
    @DisplayName("확정(CONFIRMED)된 주문은 취소할 수 없다")
    void cancelOrderFail_Confirmed() {
        // given
        Order order = createPendingOrder();
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> order.cancel(now.plusDays(10), now))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_CONFIRMED_ORDER.getMessage());
    }

    @Test
    @DisplayName("부분 환불 수량만큼 주문 수량을 차감한다")
    void applyRefund_partially_decreases_order_count() {
        Order order = Order.register(
                UUID.randomUUID(), UUID.randomUUID(), 3,
                BigInteger.valueOf(10000), "테스트 코스"
        );
        order.completePayment();

        order.applyRefund(1, LocalDateTime.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getOrderCount()).isEqualTo(2);
        assertThat(order.getTotalPriceSnap()).isEqualTo(BigInteger.valueOf(20000));
        assertThat(order.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("남은 수량을 모두 환불하면 주문이 CANCELLED 상태가 된다")
    void applyRefund_all_changes_status_to_cancelled() {
        Order order = createPendingOrder();
        order.completePayment();
        LocalDateTime now = LocalDateTime.now();

        order.applyRefund(1, now);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getOrderCount()).isZero();
        assertThat(order.getTotalPriceSnap()).isEqualTo(BigInteger.ZERO);
        assertThat(order.getCancelledAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("PAID 상태가 아니면 환불 수량 차감을 할 수 없다")
    void applyRefund_throws_when_order_not_paid() {
        Order order = createPendingOrder();

        assertThatThrownBy(() -> order.applyRefund(1, LocalDateTime.now()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS.getMessage());
    }

    private Order createPendingOrder() {
        return Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트 코스");
    }
}
