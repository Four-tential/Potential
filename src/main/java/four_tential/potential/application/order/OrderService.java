package four_tential.potential.application.order;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(UUID memberId, OrderCreateRequest request) {
        // TODO: 강의 도메인 서비스의 실제 강의 정보를 조회하도록 수정 필요 (가격 변조 방지)
        // 현재는 강의 도메인이 구축되지 않아 임시로 요청 데이터를 신뢰함
        BigInteger coursePrice = request.priceSnap();
        String courseTitle = request.titleSnap();

        Order order = Order.register(
                memberId,
                request.courseId(),
                request.orderCount(),
                coursePrice,
                courseTitle
        );
        return orderRepository.save(order);
    }

}
