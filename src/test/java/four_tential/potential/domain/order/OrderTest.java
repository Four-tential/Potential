package four_tential.potential.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    @DisplayName("주문을 성공적으로 생성합니다")
    void register() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        int orderCount = 2;
        BigInteger priceSnap = BigInteger.valueOf(10000);
        String titleSnap = "테스트 코스";

        // when
        LocalDateTime beforeRegister = LocalDateTime.now();
        Order order = Order.register(memberId, courseId, orderCount, priceSnap, titleSnap);
        LocalDateTime afterRegister = LocalDateTime.now();

        // then
        assertThat(order).isNotNull();
        assertThat(order.getMemberId()).isEqualTo(memberId);
        assertThat(order.getCourseId()).isEqualTo(courseId);
        assertThat(order.getOrderCount()).isEqualTo(orderCount);
        assertThat(order.getPriceSnap()).isEqualTo(priceSnap);
        assertThat(order.getTotalPriceSnap()).isEqualTo(priceSnap.multiply(BigInteger.valueOf(orderCount)));
        assertThat(order.getTitleSnap()).isEqualTo(titleSnap);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getExpireAt())
                .isAfterOrEqualTo(beforeRegister.plusMinutes(Order.ORDER_EXPIRATION_MINUTES))
                .isBeforeOrEqualTo(afterRegister.plusMinutes(Order.ORDER_EXPIRATION_MINUTES));
    }
}
