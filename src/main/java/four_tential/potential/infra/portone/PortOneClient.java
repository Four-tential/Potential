package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import io.portone.sdk.server.payment.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient implements PaymentGateway {

    private static final String API_BASE = "https://api.portone.io";

    private final PortOneProperties portOneProperties;

    // pgKey로 PortOne 서버에 실제 결제 정보를 조회
    @Override
    public PaymentGatewayResponse getPayment(String pgKey) {
        try (io.portone.sdk.server.PortOneClient client = createClient()) {
            Payment payment = client.getPayment().getPayment(pgKey).join();

            if (!(payment instanceof Payment.Recognized recognized)) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
            }

            return new PaymentGatewayResponse(
                    recognized.getId(),
                    statusOf(payment),
                    paidAmountOf(recognized),
                    payMethodOf(recognized.getMethod())
            );
        } catch (CompletionException e) {
            log.error("[PORTONE] getPayment failed. pgKey={}", pgKey, e);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        }
    }

    // 검증 실패나 웹훅 최종 검증 실패 시 PortOne 결제를 취소 요청
    @Override
    public void cancelPayment(PaymentGatewayRequest request) {
        try (io.portone.sdk.server.PortOneClient client = createClient()) {
            client.getPayment()
                    .cancelPayment(
                            request.pgKey(),
                            request.amount(),
                            null,
                            null,
                            request.reason(),
                            CancelRequester.Customer.INSTANCE,
                            null,
                            null,
                            null,
                            null,
                            null
                    )
                    .join();
        } catch (CompletionException e) {
            log.error("[PORTONE] cancelPayment failed. pgKey={} amount={}",
                    request.pgKey(), request.amount(), e);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        }
    }

    private io.portone.sdk.server.PortOneClient createClient() {
        return new io.portone.sdk.server.PortOneClient(
                portOneProperties.getApiSecret(),
                API_BASE,
                portOneProperties.getStoreId()
        );
    }

    // application 계층이 PortOne SDK 타입을 직접 알지 않게 하기 위한 변환 메서드
    private String statusOf(Payment payment) {
        if (payment instanceof PaidPayment) {
            return "PAID";
        }
        if (payment instanceof FailedPayment) {
            return "FAILED";
        }
        if (payment instanceof CancelledPayment) {
            return "CANCELLED";
        }
        if (payment instanceof PartialCancelledPayment) {
            return "PARTIAL_CANCELLED";
        }
        if (payment instanceof ReadyPayment) {
            return "READY";
        }
        if (payment instanceof PayPendingPayment) {
            return "PAY_PENDING";
        }
        if (payment instanceof VirtualAccountIssuedPayment) {
            return "VIRTUAL_ACCOUNT_ISSUED";
        }
        return payment.getClass().getSimpleName();
    }

    private Long paidAmountOf(Payment.Recognized payment) {
        if (payment.getAmount() == null) {
            return 0L;
        }
        return payment.getAmount().getPaid();
    }

    private String payMethodOf(PaymentMethod method) {
        if (method instanceof PaymentMethodCard) {
            return "card";
        }
        if (method instanceof PaymentMethodEasyPay) {
            return "easyPay";
        }
        return "unknown";
    }
}
