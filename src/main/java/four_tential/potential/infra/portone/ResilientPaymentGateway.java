package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "payment.gateway.stub",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class ResilientPaymentGateway implements PaymentGateway {

    private final PortOneClient delegate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public PaymentGatewayResponse getPayment(String pgKey) {
        return executeSupplier("portoneGetPayment", pgKey, () -> delegate.getPayment(pgKey));
    }

    @Override
    public void cancelPayment(PaymentGatewayRequest request) {
        executeRunnable("portoneCancelPayment", request.pgKey(), () -> delegate.cancelPayment(request));
    }

    private <T> T executeSupplier(String breakerName, String target, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(breakerName);

        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException e) {
            log.warn("[PORTONE_CIRCUIT_BREAKER] 호출 차단. breaker={} target={} state={}",
                    breakerName, target, circuitBreaker.getState());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        }
    }

    private void executeRunnable(String breakerName, String target, Runnable runnable) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(breakerName);

        try {
            circuitBreaker.executeSupplier(() -> {
                runnable.run();
                return null;
            });
        } catch (CallNotPermittedException e) {
            log.warn("[PORTONE_CIRCUIT_BREAKER] 호출 차단. breaker={} target={} state={}",
                    breakerName, target, circuitBreaker.getState());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED);
        }
    }
}
