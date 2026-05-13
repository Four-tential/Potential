package four_tential.potential.application.payment;

import four_tential.potential.application.order.OrderService;
import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.repository.CourseCancelOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundFacadeMetricsTest {

    @InjectMocks
    private RefundFacade refundFacade;

    @Mock
    private RefundService refundService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderService orderService;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private PaymentDistributedLockExecutor paymentLockExecutor;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private CourseCancelOutboxRepository courseCancelOutboxRepository;

    @Mock
    private PaymentMetrics paymentMetrics;

    @Test
    @DisplayName("refundPaidOrderByStudent는 주문 조회 단계에서 실패해도 fail 메트릭을 기록한다")
    void refundByStudent_records_fail_metrics_when_order_lookup_fails() {
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> refundFacade.refundPaidOrderByStudent(memberId, orderId, 1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getMessage());

        verify(paymentMetrics).recordRefundRequest("fail");
        verify(paymentMetrics).recordRefundDuration(eq("fail"), anyLong());
        verify(paymentService, never()).findByOrderId(any());
        verify(paymentGateway, never()).cancelPayment(any());
    }
}
