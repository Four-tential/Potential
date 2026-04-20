package four_tential.potential.application.order;

import four_tential.potential.application.payment.RefundFacade;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.order.WaitingStatus;
import four_tential.potential.presentation.order.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final WaitingListService waitingListService;
    private final RefundFacade refundFacade;

    public OrderPlaceResult placeOrder(UUID memberId, OrderCreateRequest request) {
        // 잔여석 점유 시도
        if (waitingListService.tryOccupyingSeat(request.courseId(), memberId, request.orderCount())) {
            try {
                // 잔여석 점유 성공 -> 주문 생성 (DB)
                Order order = orderService.createOrder(memberId, request);
                return new OrderCreateResponse(
                        order.getId(),
                        order.getStatus().name(),
                        order.getExpireAt(),
                        OrderConstants.MESSAGE_ORDER_SUCCESS
                );
            } catch (Exception e) {
                // DB 저장 실패 시 Redis 잔여석 복구 (보상 트랜잭션)
                try {
                    waitingListService.rollbackOccupiedSeat(request.courseId(), memberId);
                } catch (Exception rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            }
        }

        // 잔여석 점유 실패 -> 대기열 진입
        waitingListService.addToWaitingList(request.courseId(), memberId);
        return new OrderWaitingResponse(
                request.courseId(),
                WaitingStatus.WAITING.name(),
                OrderConstants.MESSAGE_WAITING_COMPLETED
        );
    }

    /**
     * 주문 상세 조회
     */
    public OrderDetailResponse getOrderDetails(UUID orderId, UUID memberId) {
        Order order = orderService.getOrderDetails(orderId, memberId);
        return OrderDetailResponse.from(order);
    }

    /**
     * 나의 주문 목록 조회
     */
    public PageResponse<OrderMyListResponse> getMyOrders(UUID memberId, Pageable pageable) {
        Page<Order> orders = orderService.getMyOrders(memberId, pageable);
        Page<OrderMyListResponse> responsePage = orders.map(OrderMyListResponse::from);
        return PageResponse.register(responsePage);
    }

    /**
     * 주문 취소
     * 결제된 주문은 환불이 성공한 뒤 주문 수량과 상태를 정리한다.
     */
    public OrderCancelResponse cancelOrder(UUID orderId, UUID memberId, OrderCancelRequest request) {
        // 1. 본인 주문인지 확인
        Order order = orderService.getOrderDetails(orderId, memberId);

        // 2. 주문 상태 PAID이면 환불 흐름 진입
        if (order.getStatus() == OrderStatus.PAID) {
            // 2-1. 환불 처리 (내부에서 PortOne API 호출 + DB 반영)
            // RefundFacade 내부에서 recoverCapacity 호출
            refundFacade.refundPaidOrderByStudent(memberId, orderId, request.cancelCount());

            // 2-2. 환불이 완료된 주문을 다시 조회
            Order refundedOrder = orderService.getOrderDetails(orderId, memberId);
            return OrderCancelResponse.from(refundedOrder);
        }

        // cancelCount != orderCount면 예외
        validateFullCancel(order, request.cancelCount());

        // 주문 상태 CANCELLED 변경
        Order cancelledOrder = orderService.cancelOrder(orderId, memberId);

        // Redis 잔여석 복구 (취소된 수량만큼) 및 점유 정보 정리
        waitingListService.recoverCapacity(
                cancelledOrder.getCourseId(),
                cancelledOrder.getMemberId(),
                cancelledOrder.getOrderCount()
        );

        return OrderCancelResponse.from(cancelledOrder);
    }

    private void validateFullCancel(Order order, int cancelCount) {
        if (cancelCount != order.getOrderCount()) {
            throw new ServiceErrorException(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT);
        }
    }
}
