package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * local + perf 프로필에서만 활성화하는 PortOne 대체 스텁이다.
 *
 * 환불 성능 테스트에서는 실제 외부 취소 API를 반복 호출하면
 * 로컬에서 재현성과 속도가 크게 흔들릴 수 있다.
 * 그래서 취소 호출은 성공으로 처리하되, 조회 응답은 DB에 저장된 payment row를 기준으로 돌려준다.
 */
@Slf4j
@Primary
@Component
@Profile("local & perf")
@ConditionalOnProperty(prefix = "payment.gateway.stub", name = "enabled", havingValue = "true")
public class LocalPaymentGatewayStub implements PaymentGateway {

    private final PaymentRepository paymentRepository;
    private final LocalPaymentGatewayStubProperties stubProperties;

    public LocalPaymentGatewayStub(
            PaymentRepository paymentRepository,
            LocalPaymentGatewayStubProperties stubProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.stubProperties = stubProperties;
    }

    @Override
    public PaymentGatewayResponse getPayment(String pgKey) {
        sleepQuietly(stubProperties.getGetPaymentDelay());
        log.debug("[LOCAL_PAYMENT_GATEWAY] getPayment stub called. pgKey={}", pgKey);

        Payment payment = paymentRepository.findByPgKey(pgKey)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED));

        return new PaymentGatewayResponse(
                payment.getPgKey(),
                toGatewayStatus(payment.getStatus()),
                payment.getPaidTotalPrice(),
                toGatewayPayMethod(payment.getPayWay())
        );
    }

    @Override
    public void cancelPayment(PaymentGatewayRequest request) {
        sleepQuietly(stubProperties.getCancelPaymentDelay());
        log.debug(
                "[LOCAL_PAYMENT_GATEWAY] cancelPayment stub called. pgKey={} amount={} currentCancellableAmount={} reason={}",
                request.pgKey(),
                request.amount(),
                request.currentCancellableAmount(),
                request.reason()
        );
    }

    private String toGatewayStatus(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "READY";
            case PAID -> "PAID";
            case FAILED -> "FAILED";
            case PART_REFUNDED -> "PARTIAL_CANCELLED";
            case REFUNDED -> "CANCELLED";
        };
    }

    private String toGatewayPayMethod(PaymentPayWay payWay) {
        return switch (payWay) {
            case CARD -> "card";
            case EASY_PAY -> "easyPay";
        };
    }

    private void sleepQuietly(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LOCAL_PAYMENT_GATEWAY] stub delay interrupted. delay={}ms", delay.toMillis());
        }
    }
}
