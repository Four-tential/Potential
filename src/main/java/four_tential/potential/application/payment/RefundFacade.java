package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.presentation.payment.dto.RefundPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundService refundService;
    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;

    /**
     * 환불 가능 여부 조회
     */

    public RefundPreviewResponse getRefundPreview(UUID memberId, UUID paymentId) {
        Payment payment = paymentService.getById(paymentId);
        Order order = getOrder(payment.getOrderId());
        Course course = getCourse(order.getCourseId());

        return refundService.getRefundPreview(
                payment,
                memberId,
                order.getTitleSnap(),
                course.getStartAt(),
                order.getOrderCount()
        );
    }

    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceErrorException(OrderExceptionEnum.ERR_NOT_FOUND_ORDER));
    }

    private Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_COURSE_NOT_FOUND));
    }
}
