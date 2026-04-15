package four_tential.potential.application.order;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private OrderService orderService;

    @Test
    @DisplayName("주문을 성공적으로 생성하고 저장한다")
    void createOrder_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        OrderCreateRequest request = new OrderCreateRequest(
                courseId,
                2,
                BigInteger.valueOf(50000),
                "테스트 강의"
        );

        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Order result = orderService.createOrder(memberId, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getCourseId()).isEqualTo(courseId);
        assertThat(result.getOrderCount()).isEqualTo(2);
        assertThat(result.getPriceSnap()).isEqualTo(BigInteger.valueOf(50000));
        
        verify(orderRepository).save(any(Order.class));
    }
}
