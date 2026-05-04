package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResilientPaymentGatewayTest {

    @Mock
    private PortOneClient delegate;

    private CircuitBreakerRegistry registry;
    private ResilientPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(ServiceErrorException.class)
                .build();

        registry = CircuitBreakerRegistry.of(config);
        gateway = new ResilientPaymentGateway(delegate, registry);
    }

    @Test
    @DisplayName("getPayment returns the delegate response while the circuit is closed")
    void getPayment_returns_delegate_response_when_closed() {
        PaymentGatewayResponse response = new PaymentGatewayResponse("pg-1", "PAID", 1000L, "card");
        given(delegate.getPayment("pg-1")).willReturn(response);

        PaymentGatewayResponse actual = gateway.getPayment("pg-1");

        assertThat(actual).isEqualTo(response);
        assertThat(registry.circuitBreaker("portoneGetPayment").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("getPayment opens the circuit after repeated failures and then fails fast")
    void getPayment_opens_circuit_and_then_fails_fast() {
        given(delegate.getPayment("pg-1"))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED));

        assertThatThrownBy(() -> gateway.getPayment("pg-1"))
                .isInstanceOf(ServiceErrorException.class);
        assertThatThrownBy(() -> gateway.getPayment("pg-1"))
                .isInstanceOf(ServiceErrorException.class);

        CircuitBreaker circuitBreaker = registry.circuitBreaker("portoneGetPayment");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> gateway.getPayment("pg-1"))
                .isInstanceOf(ServiceErrorException.class);

        verify(delegate, times(2)).getPayment("pg-1");
    }

    @Test
    @DisplayName("getPayment does not call the delegate after the circuit is already open")
    void getPayment_does_not_call_delegate_after_circuit_opens() {
        given(delegate.getPayment("pg-1"))
                .willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED));

        assertThatThrownBy(() -> gateway.getPayment("pg-1"))
                .isInstanceOf(ServiceErrorException.class);
        assertThatThrownBy(() -> gateway.getPayment("pg-1"))
                .isInstanceOf(ServiceErrorException.class);
        assertThatThrownBy(() -> gateway.getPayment("pg-2"))
                .isInstanceOf(ServiceErrorException.class);

        verify(delegate, times(2)).getPayment("pg-1");
        verify(delegate, never()).getPayment("pg-2");
    }

    @Test
    @DisplayName("cancelPayment delegates successfully while the circuit is closed")
    void cancelPayment_delegates_successfully_when_closed() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-1", 1000L, "CANCEL");

        gateway.cancelPayment(request);

        verify(delegate).cancelPayment(request);
        assertThat(registry.circuitBreaker("portoneCancelPayment").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("cancelPayment opens the circuit after repeated failures and then fails fast")
    void cancelPayment_opens_circuit_and_then_fails_fast() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-1", 1000L, "CANCEL");

        willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED))
                .given(delegate).cancelPayment(request);

        assertThatThrownBy(() -> gateway.cancelPayment(request))
                .isInstanceOf(ServiceErrorException.class);
        assertThatThrownBy(() -> gateway.cancelPayment(request))
                .isInstanceOf(ServiceErrorException.class);

        CircuitBreaker circuitBreaker = registry.circuitBreaker("portoneCancelPayment");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> gateway.cancelPayment(request))
                .isInstanceOf(ServiceErrorException.class);

        verify(delegate, times(2)).cancelPayment(request);
    }

    @Test
    @DisplayName("cancelPayment does not call the delegate after the circuit is already open")
    void cancelPayment_does_not_call_delegate_after_circuit_opens() {
        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-1", 1000L, "CANCEL");
        PaymentGatewayRequest blockedRequest = PaymentGatewayRequest.of("pg-2", 1000L, "CANCEL");

        willThrow(new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_GATEWAY_FAILED))
                .given(delegate).cancelPayment(request);

        assertThatThrownBy(() -> gateway.cancelPayment(request))
                .isInstanceOf(ServiceErrorException.class);
        assertThatThrownBy(() -> gateway.cancelPayment(request))
                .isInstanceOf(ServiceErrorException.class);
        assertThatThrownBy(() -> gateway.cancelPayment(blockedRequest))
                .isInstanceOf(ServiceErrorException.class);

        verify(delegate, times(2)).cancelPayment(request);
        verify(delegate, never()).cancelPayment(blockedRequest);
    }
}
