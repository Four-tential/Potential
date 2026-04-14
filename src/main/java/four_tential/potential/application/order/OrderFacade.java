package four_tential.potential.application.order;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.WaitingStatus;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import four_tential.potential.presentation.order.dto.OrderCreateResponse;
import four_tential.potential.presentation.order.dto.OrderPlaceResult;
import four_tential.potential.presentation.order.dto.OrderWaitingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final WaitingListService waitingListService;

    public OrderPlaceResult placeOrder(UUID memberId, OrderCreateRequest request) {
        // 재고 점유 시도
        if (waitingListService.tryOccupyingStock(request.courseId(), memberId)) {
            try {
                // 재고 점유 성공 -> 주문 생성 (DB)
                Order order = orderService.createOrder(memberId, request);
                return new OrderCreateResponse(
                        order.getId(),
                        order.getStatus().name(),
                        order.getExpireAt(),
                        OrderConstants.MESSAGE_ORDER_SUCCESS
                );
            } catch (Exception e) {
                // DB 저장 실패 시 Redis 재고 복구 (보상 트랜잭션)
                waitingListService.rollbackOccupiedStock(request.courseId(), memberId);
                throw e;
            }
        }

        // 재고 점유 실패 -> 대기열 진입
        waitingListService.addToWaitingList(request.courseId(), memberId);
        return new OrderWaitingResponse(
                request.courseId(),
                WaitingStatus.WAITING.name(),
                OrderConstants.MESSAGE_WAITING_COMPLETED
        );
    }
}
