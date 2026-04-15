package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMyListResponseTest {

    @Test
    @DisplayName("Order 엔티티로부터 OrderMyListResponse를 성공적으로 생성한다")
    void from_order_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        BigInteger price = BigInteger.valueOf(10000);
        String title = "테스트 강의";
        int count = 2;

        Order order = Order.register(memberId, courseId, count, price, title);

        // when
        OrderMyListResponse response = OrderMyListResponse.from(order);

        // then
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.courseId()).isEqualTo(order.getCourseId());
        assertThat(response.titleSnap()).isEqualTo(order.getTitleSnap());
        assertThat(response.totalPriceSnap()).isEqualTo(order.getTotalPriceSnap());
        assertThat(response.status()).isEqualTo(order.getStatus());
        assertThat(response.createdAt()).isEqualTo(order.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(order.getUpdatedAt());
        assertThat(response.expireAt()).isEqualTo(order.getExpireAt());
    }
}
