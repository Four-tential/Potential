package four_tential.potential.infra.portone;

import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local 성능 테스트 전용 결제 게이트웨이 스텁.
 *
 * 학생 환불 시나리오는 실제 PortOne 취소 API까지 나가면
 * 로컬에서 반복 가능한 baseline을 만들기 어렵다.
 * local 프로필에서는 내부 환불 오케스트레이션, 락, DB 반영,
 * 후속 조회 성능에 집중하기 위해 취소 요청을 성공으로 처리한다.
 */
@Slf4j
@Primary
@Component
@Profile("local")
public class LocalPaymentGatewayStub implements PaymentGateway {

    @Override
    public PaymentGatewayResponse getPayment(String pgKey) {
        log.info("[LOCAL_PAYMENT_GATEWAY] getPayment stub called. pgKey={}", pgKey);
        return new PaymentGatewayResponse(pgKey, "PAID", 50000L, "card");
    }

    @Override
    public void cancelPayment(PaymentGatewayRequest request) {
        log.info(
                "[LOCAL_PAYMENT_GATEWAY] cancelPayment stub called. pgKey={} amount={} currentCancellableAmount={} reason={}",
                request.pgKey(),
                request.amount(),
                request.currentCancellableAmount(),
                request.reason()
        );
    }
}
