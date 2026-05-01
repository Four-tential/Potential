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
    @DisplayName("정상일 때는 delegate 응답을 그대로 반환한다")
    void getPayment_returns_delegate_response_when_closed() {
        PaymentGatewayResponse response = new PaymentGatewayResponse("pg-1", "PAID", 1000L, "card");
        given(delegate.getPayment("pg-1")).willReturn(response);

        PaymentGatewayResponse actual = gateway.getPayment("pg-1");

        assertThat(actual).isEqualTo(response);
        assertThat(registry.circuitBreaker("portoneGetPayment").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("getPayment가 연속 실패하면 회로가 열리고 이후 호출은 빠르게 실패한다")
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
    @DisplayName("cancelPayment가 연속 실패하면 회로가 열리고 이후 호출은 빠르게 실패한다")
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
}