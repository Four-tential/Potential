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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient implements PaymentGateway {

    private final PortOneProperties portOneProperties;

    // pgKey로 PortOne 서버의 실제 결제 정보를 조회
    @Override
    public PaymentGatewayResponse getPayment(String pgKey) {
        try (io.portone.sdk.server.PortOneClient client = createClient()) {
            Payment payment = await(client.getPayment().getPayment(pgKey), "getPayment", pgKey);

            if (!(payment instanceof Payment.Recognized recognized)) {
                throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
            }

            return new PaymentGatewayResponse(
                    recognized.getId(),
                    statusOf(payment),
                    paidAmountOf(recognized),
                    payMethodOf(recognized.getMethod())
            );
        }
    }

    // 검증 실패 webhook 최종 검증 실패 시 PortOne 결제를 취소 요청
    @Override
    public void cancelPayment(PaymentGatewayRequest request) {
        try (io.portone.sdk.server.PortOneClient client = createClient()) {
            await(
                    client.getPayment().cancelPayment(
                            request.pgKey(),                      // 1. paymentId
                            request.amount(),                     // 2. cancellationAmount
                            null,                                 // 3. taxFreeAmount
                            null,                                 // 4. vatAmount
                            request.reason(),                     // 5. reason
                            CancelRequester.Customer.INSTANCE,    // 6. requester
                            null,                                 // 7. promotionDiscountRetainOption
                            request.currentCancellableAmount(),   // 8. currentCancellableAmount
                            null,                                 // 9. refundBankCode
                            null,                                 // 10. refundAccountNumber
                            null                                  // 11. refundAccountHolderName
                    ),
                    "cancelPayment",
                    request.pgKey()
            );
        }
    }

    private io.portone.sdk.server.PortOneClient createClient() {
        return new io.portone.sdk.server.PortOneClient(
                portOneProperties.getApiSecret(),
                portOneProperties.getApiBase(),
                portOneProperties.getStoreId()
        );
    }

    // application.yml의 Duration 설정값으로 SDK 대기 시간을 직접 제어한다.
    private <T> T await(CompletableFuture<T> future, String action, String pgKey) {
        try {
            return future.get(portOneProperties.getSdkTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("[PORTONE] {} timeout. pgKey={} timeout={}ms",
                    action, pgKey, portOneProperties.getSdkTimeout().toMillis(), e);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[PORTONE] {} interrupted. pgKey={}", action, pgKey, e);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        } catch (ExecutionException e) {
            log.error("[PORTONE] {} failed. pgKey={}", action, pgKey, e.getCause());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        }
    }

    // application 계층에서 PortOne SDK 타입을 직접 알지 않게 하기 위한 변환 메서드
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
        return Optional.ofNullable(payment.getAmount())
                .map(PaymentAmount::getPaid)
                .orElse(0L);
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
