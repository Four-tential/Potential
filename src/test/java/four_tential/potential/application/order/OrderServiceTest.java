package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateRequest;
import four_tential.potential.presentation.order.dto.OrderAdminStatusUpdateResponse;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import four_tential.potential.presentation.order.dto.OrderInventoryReconcileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private WaitingListService waitingListService;
    @Mock private ApplicationContext applicationContext;
    @InjectMocks @Spy private OrderService orderService;

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

    @Test
    @DisplayName("주문 상세 조회 성공 시 주문 정보를 반환한다")
    void getOrderDetails_success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Order order = mock(Order.class);
        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.of(order));

        // when
        Order result = orderService.getOrderDetails(orderId, memberId);

        // then
        assertThat(result).isEqualTo(order);
        verify(orderRepository).findOrderDetailsById(orderId, memberId);
    }

    @Test
    @DisplayName("주문 상세 조회 실패 시 예외를 발생시킨다")
    void getOrderDetails_fail_notFound() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.getOrderDetails(orderId, memberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getMessage());
    }

    @Test
    @DisplayName("나의 주문 목록을 성공적으로 조회한다")
    void getMyOrders_success() {
        // given
        UUID memberId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Order order = mock(Order.class);
        Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);
        
        given(orderRepository.findMyOrders(memberId, pageable)).willReturn(orderPage);

        // when
        Page<Order> result = orderService.getMyOrders(memberId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(orderRepository).findMyOrders(memberId, pageable);
    }

    @Test
    @DisplayName("결제 완료 처리를 성공한다")
    void completePayment_success() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = spy(Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트"));
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        orderService.completePayment(orderId);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("단일 주문 만료 처리를 성공적으로 수행한다")
    void expireOrderInNewTransaction_success() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // given
            UUID orderId = UUID.randomUUID();
            UUID courseId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Order order = spy(Order.register(memberId, courseId, 1, BigInteger.valueOf(10000), "만료대상"));
            ReflectionTestUtils.setField(order, "id", orderId);
            
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            // when
            boolean result = orderService.expireOrderInNewTransaction(orderId);

            // then
            assertThat(result).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            verify(orderRepository).saveAndFlush(order);

            // TransactionSynchronizationManager.registerSynchronization 에 전달된 콜백 실행 유도
            var captor = org.mockito.ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()));
            captor.getValue().afterCommit();

            verify(waitingListService).rollbackOccupiedSeat(courseId, memberId);
        }
    }

    @Test
    @DisplayName("단일 주문 만료 처리 중 예외가 발생하면 false를 반환한다")
    void expireOrderInNewTransaction_fail_on_exception() {
        // given
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willThrow(new RuntimeException("DB Error"));

        // when
        boolean result = orderService.expireOrderInNewTransaction(orderId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("만료된 주문들을 배치 단위로 처리한다")
    void processExpiredBatch_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        UUID orderId = UUID.randomUUID();
        Order expiredOrder = mock(Order.class);
        given(expiredOrder.getId()).willReturn(orderId);
        
        Slice<Order> expiredSlice = new SliceImpl<>(List.of(expiredOrder));
        given(orderRepository.findAllByStatusAndExpireAtBefore(any(), any(), any())).willReturn(expiredSlice);
        
        // Self-invocation 모킹
        given(applicationContext.getBean(OrderService.class)).willReturn(orderService);
        doReturn(true).when(orderService).expireOrderInNewTransaction(orderId);

        // when
        OrderService.OrderBatchResult result = orderService.processExpiredBatch(now, batchSize);

        // then
        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        verify(orderService).expireOrderInNewTransaction(orderId);
    }

    @Test
    @DisplayName("만료된 주문이 없으면 처리 건수가 0이다")
    void processExpiredBatch_empty() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        given(orderRepository.findAllByStatusAndExpireAtBefore(any(), any(), any()))
                .willReturn(new SliceImpl<>(Collections.emptyList()));

        // when
        OrderService.OrderBatchResult result = orderService.processExpiredBatch(now, batchSize);

        // then
        assertThat(result.successCount()).isZero();
        assertThat(result.fetchedCount()).isZero();
    }

    @Test
    @DisplayName("단일 주문 확정 처리를 성공적으로 수행한다")
    void confirmOrderInNewTransaction_success() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = spy(Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "확정대상"));
        order.completePayment(); // PAID 상태
        ReflectionTestUtils.setField(order, "id", orderId);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        boolean result = orderService.confirmOrderInNewTransaction(orderId);

        // then
        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    @DisplayName("단일 주문 확정 처리 중 예외가 발생하면 false를 반환한다")
    void confirmOrderInNewTransaction_fail_on_exception() {
        // given
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willThrow(new RuntimeException("DB Error"));

        // when
        boolean result = orderService.confirmOrderInNewTransaction(orderId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("확정 대상 주문들을 배치 단위로 처리한다")
    void processConfirmedBatch_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 100;
        UUID orderId = UUID.randomUUID();
        Order paidOrder = mock(Order.class);
        given(paidOrder.getId()).willReturn(orderId);

        given(orderRepository.findPaidOrdersToConfirm(eq(now), any())).willReturn(List.of(paidOrder));

        // Self-invocation 모킹
        given(applicationContext.getBean(OrderService.class)).willReturn(orderService);
        doReturn(true).when(orderService).confirmOrderInNewTransaction(orderId);

        // when
        OrderService.OrderBatchResult result = orderService.processConfirmedBatch(now, batchSize);

        // then
        assertThat(result.fetchedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        verify(orderService).confirmOrderInNewTransaction(orderId);
    }

    @Test
    @DisplayName("주문 취소 처리를 성공하고 취소된 주문을 반환한다")
    void cancelOrder_success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Order order = spy(Order.register(memberId, courseId, 2, BigInteger.valueOf(20000), "테스트"));
        Course course = mock(Course.class);

        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(course.getStartAt()).willReturn(LocalDateTime.now().plusDays(10));

        // when
        Order result = orderService.cancelOrder(orderId, memberId);

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(order).cancel(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("PENDING 상태의 주문이라도 코스 시작 7일 이내라면 취소 시 예외가 발생한다")
    void cancelOrder_fail_tooLate() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        Order order = Order.register(memberId, courseId, 2, BigInteger.valueOf(20000), "테스트");
        Course course = mock(Course.class);

        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.of(order));
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(course.getStartAt()).willReturn(LocalDateTime.now().plusDays(6));

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(orderId, memberId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_DATETIME.getMessage());
    }

    @Test
    @DisplayName("환불 완료 후 주문 수량을 차감한다")
    void applyRefund_success() {
        UUID orderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = Order.register(memberId, courseId, 3, BigInteger.valueOf(20000), "테스트");
        order.completePayment();

        given(orderRepository.findOrderDetailsById(orderId, memberId)).willReturn(Optional.of(order));

        Order result = orderService.applyRefund(orderId, memberId, 1);

        assertThat(result.getOrderCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).findOrderDetailsById(orderId, memberId);
    }

    @Test
    @DisplayName("관리자가 주문 상태를 강제로 변경하면 응답을 반환하고 취소 시 재고를 복구한다")
    void updateOrderStatusByAdmin_Success() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // given
            UUID orderId = UUID.randomUUID();
            UUID courseId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Order order = spy(Order.register(memberId, courseId, 1, BigInteger.valueOf(10000), "테스트"));
            ReflectionTestUtils.setField(order, "id", orderId);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.CANCELLED, "입금 미확인");

            // when
            OrderAdminStatusUpdateResponse response = orderService.updateOrderStatusByAdmin(orderId, request);

            // then
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.previousStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.currentStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            verify(orderRepository).findById(orderId);

            // TransactionSynchronizationManager.registerSynchronization 에 전달된 콜백 실행 유도
            var captor = org.mockito.ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(captor.capture()));
            captor.getValue().afterCommit();

            verify(waitingListService).rollbackOccupiedSeat(courseId, memberId);
        }
    }

    @Test
    @DisplayName("관리자가 주문 상태를 PAID로 변경하는 경우 재고를 복구하지 않는다")
    void updateOrderStatusByAdmin_To_Paid_No_Restoration() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // given
            UUID orderId = UUID.randomUUID();
            Order order = spy(Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트"));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.PAID, "강제 결제완료");

            // when
            orderService.updateOrderStatusByAdmin(orderId, request);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()), never());
            verify(waitingListService, never()).rollbackOccupiedSeat(any(), any());
        }
    }

    @Test
    @DisplayName("이미 취소된 주문을 다른 상태로 변경하는 경우 재고를 복구하지 않는다")
    void updateOrderStatusByAdmin_From_Cancelled_No_Restoration() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // given
            UUID orderId = UUID.randomUUID();
            Order order = Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트");
            ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.PENDING, "오처리 복구");

            // when
            orderService.updateOrderStatusByAdmin(orderId, request);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()), never());
            verify(waitingListService, never()).rollbackOccupiedSeat(any(), any());
        }
    }

    @Test
    @DisplayName("PAID 상태인 주문을 EXPIRED로 변경하는 경우 재고를 복구한다")
    void updateOrderStatusByAdmin_From_Paid_To_Expired_Restoration() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // given
            UUID orderId = UUID.randomUUID();
            UUID courseId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Order order = Order.register(memberId, courseId, 1, BigInteger.valueOf(10000), "테스트");
            ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            OrderAdminStatusUpdateRequest request = new OrderAdminStatusUpdateRequest(OrderStatus.EXPIRED, "기간 만료 처리");

            // when
            orderService.updateOrderStatusByAdmin(orderId, request);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }
    }

    @Test
    @DisplayName("재고 정합성 복구를 수행하고 결과를 반환한다")
    void reconcileInventory_success() {
        // given
        UUID courseId = UUID.randomUUID();
        Course course = mock(Course.class);
        given(course.getCapacity()).willReturn(100);
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        
        // DB 점유 좌석 수 합계 모킹 (PENDING 2 + PAID 3 + CONFIRMED 5 = 10)
        given(orderRepository.sumOrderCountByCourseIdAndStatuses(
                eq(courseId),
                eq(List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CONFIRMED))
        )).willReturn(10);

        // when
        OrderInventoryReconcileResponse response = orderService.reconcileInventory(courseId);

        // then
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.totalCapacity()).isEqualTo(100);
        assertThat(response.dbOccupiedSeats()).isEqualTo(10);
        assertThat(response.reconciledCapacity()).isEqualTo(90L); // 100 - 10

        verify(orderRepository).sumOrderCountByCourseIdAndStatuses(
                eq(courseId),
                eq(List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CONFIRMED))
        );
        verify(waitingListService).updateCapacity(courseId, 90L);
    }
}
