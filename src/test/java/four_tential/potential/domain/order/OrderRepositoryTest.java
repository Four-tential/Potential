package four_tential.potential.domain.order;

import four_tential.potential.infra.redis.RedisTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderRepositoryTest extends RedisTestContainer {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("주문 ID와 본인 ID로 주문 상세 정보를 성공적으로 조회한다")
    void findOrderDetailsById_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = Order.register(
                memberId,
                courseId,
                1,
                BigInteger.valueOf(50000),
                "테스트 강의"
        );
        Order savedOrder = orderRepository.save(order);
        UUID orderId = savedOrder.getId();

        // when
        Optional<Order> result = orderRepository.findOrderDetailsById(orderId, memberId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(orderId);
        assertThat(result.get().getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("타인의 주문 ID로 조회 시 빈 Optional을 반환한다")
    void findOrderDetailsById_fail_unauthorized() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID anotherMemberId = UUID.randomUUID();
        Order order = Order.register(
                memberId,
                UUID.randomUUID(),
                1,
                BigInteger.valueOf(50000),
                "테스트 강의"
        );
        Order savedOrder = orderRepository.save(order);

        // when: 본인 주문이 아닌 ID로 조회 시도
        Optional<Order> result = orderRepository.findOrderDetailsById(savedOrder.getId(), anotherMemberId);

        // then
        assertThat(result).isEmpty();
    }
}
