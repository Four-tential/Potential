package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OrderMyListResponseTest {

    @Test
    @DisplayName("Order 엔티티로부터 OrderMyListResponse를 성공적으로 생성한다")
    void from_order_success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 10, 0);
        LocalDateTime updatedAt = createdAt.plusMinutes(1);
        LocalDateTime expireAt = createdAt.plusMinutes(10);
        String title = "테스트 강의";
        BigInteger totalPrice = BigInteger.valueOf(20000);

        Order order = mock(Order.class);
        given(order.getId()).willReturn(orderId);
        given(order.getCourseId()).willReturn(courseId);
        given(order.getTitleSnap()).willReturn(title);
        given(order.getTotalPriceSnap()).willReturn(totalPrice);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getCreatedAt()).willReturn(createdAt);
        given(order.getUpdatedAt()).willReturn(updatedAt);
        given(order.getExpireAt()).willReturn(expireAt);

        // when
        OrderMyListResponse response = OrderMyListResponse.from(order);

        // then
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.titleSnap()).isEqualTo(title);
        assertThat(response.totalPriceSnap()).isEqualTo(totalPrice);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.expireAt()).isEqualTo(expireAt);
    }
}
