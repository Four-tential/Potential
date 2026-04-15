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
    
    @Test
    @DisplayName("주문 목록 조회 시 타인의 주문은 포함되지 않는다")
    void findMyOrders_exclude_others() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();

        Order myOrder = Order.register(memberId, UUID.randomUUID(), 1, BigInteger.valueOf(10000), "내 강의");
        Order otherOrder = Order.register(otherMemberId, UUID.randomUUID(), 1, BigInteger.valueOf(20000), "남의 강의");
        orderRepository.save(myOrder);
        orderRepository.save(otherOrder);

        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);

        // when
        org.springframework.data.domain.Page<Order> result = orderRepository.findMyOrders(memberId, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMemberId()).isEqualTo(memberId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("본인의 주문 목록을 최신순으로 페이징하여 조회한다")
    void findMyOrders_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();

        Order order1 = Order.register(memberId, courseId1, 1, BigInteger.valueOf(10000), "강의 1");
        Order order2 = Order.register(memberId, courseId2, 2, BigInteger.valueOf(20000), "강의 2");
        orderRepository.save(order1);
        orderRepository.save(order2);

        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);
        // when
        org.springframework.data.domain.Page<Order> result = orderRepository.findMyOrders(memberId, pageRequest);
        // then
        assertThat(result.getContent()).hasSize(2);
        // 최신순 정렬 확인 (order2가 나중에 생성되었으므로 첫 번째여야 함)
        assertThat(result.getContent().get(0).getCourseId()).isEqualTo(courseId2);
        assertThat(result.getContent().get(1).getCourseId()).isEqualTo(courseId1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
