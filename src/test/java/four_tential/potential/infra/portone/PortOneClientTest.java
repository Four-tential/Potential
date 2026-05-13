package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import io.portone.sdk.server.payment.CancelPaymentResponse;
import io.portone.sdk.server.payment.CancelRequester;
import io.portone.sdk.server.payment.CancelledPayment;
import io.portone.sdk.server.payment.FailedPayment;
import io.portone.sdk.server.payment.PartialCancelledPayment;
import io.portone.sdk.server.payment.PayPendingPayment;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.PaymentAmount;
import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.payment.PaymentMethod;
import io.portone.sdk.server.payment.PaymentMethodCard;
import io.portone.sdk.server.payment.PaymentMethodEasyPay;
import io.portone.sdk.server.payment.ReadyPayment;
import io.portone.sdk.server.payment.VirtualAccountIssuedPayment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class PortOneClientTest {

    private PortOneProperties createProperties() {
        PortOneProperties properties = new PortOneProperties();
        properties.setApiSecret("test-api-secret");
        properties.setWebhookSecret("test-webhook-secret");
        properties.setStoreId("store-test");
        properties.setChannelKey("channel-test");
        properties.setApiBase("https://api.portone.io");
        properties.setSdkTimeout(Duration.ofSeconds(10));
        return properties;
    }

    @Test
    @DisplayName("PortOneProperties로 PortOneClient를 생성한다")
    void createClient_with_properties() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
        assertThat(portOneClient).isNotNull();
    }

    @Test
    @DisplayName("PortOne 결제 조회 결과를 PaymentGatewayResponse로 변환한다")
    void getPayment_returns_gateway_response() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaidPayment paidPayment = mock(PaidPayment.class);
        PaymentAmount amount = new PaymentAmount(1000L, 0L, null, null, 0L, 1000L, 0L, 0L);

        given(paidPayment.getId()).willReturn("pg-key-1");
        given(paidPayment.getAmount()).willReturn(amount);
        given(paidPayment.getMethod()).willReturn(new PaymentMethodCard());
        given(paymentClient.getPayment("pg-key-1"))
                .willReturn(CompletableFuture.completedFuture(paidPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());

            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-1");

            assertThat(response.pgKey()).isEqualTo("pg-key-1");
            assertThat(response.status()).isEqualTo("PAID");
            assertThat(response.totalAmount()).isEqualTo(1000L);
            assertThat(response.payMethod()).isEqualTo("card");
        }
    }

    @Test
    @DisplayName("application.yml의 api-base 설정으로 SDK 클라이언트를 생성한다")
    void createClient_uses_api_base_from_properties() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaidPayment paidPayment = mock(PaidPayment.class);
        PaymentAmount amount = new PaymentAmount(1000L, 0L, null, null, 0L, 1000L, 0L, 0L);
        List<List<?>> constructorArguments = new ArrayList<>();

        given(paidPayment.getId()).willReturn("pg-key-check-base");
        given(paidPayment.getAmount()).willReturn(amount);
        given(paidPayment.getMethod()).willReturn(new PaymentMethodCard());
        given(paymentClient.getPayment("pg-key-check-base"))
                .willReturn(CompletableFuture.completedFuture(paidPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> mocked = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> {
                    constructorArguments.add(new ArrayList<>(context.arguments()));
                    given(mock.getPayment()).willReturn(paymentClient);
                }
        )) {
            new PortOneClient(createProperties()).getPayment("pg-key-check-base");

            assertThat(mocked.constructed()).hasSize(1);
            assertThat(constructorArguments.get(0)).hasSize(3);
            assertThat(constructorArguments.get(0).get(0)).isEqualTo("test-api-secret");
            assertThat(constructorArguments.get(0).get(1)).isEqualTo("https://api.portone.io");
            assertThat(constructorArguments.get(0).get(2)).isEqualTo("store-test");
        }
    }

    @Test
    @DisplayName("PortOne SDK 클라이언트는 한 번만 생성하고 재사용한다")
    void sdkClient_is_singleton_within_bean() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaidPayment firstPayment = mock(PaidPayment.class);
        PaidPayment secondPayment = mock(PaidPayment.class);
        PaymentAmount amount = new PaymentAmount(1000L, 0L, null, null, 0L, 1000L, 0L, 0L);

        given(firstPayment.getId()).willReturn("pg-key-1");
        given(firstPayment.getAmount()).willReturn(amount);
        given(firstPayment.getMethod()).willReturn(new PaymentMethodCard());

        given(secondPayment.getId()).willReturn("pg-key-2");
        given(secondPayment.getAmount()).willReturn(amount);
        given(secondPayment.getMethod()).willReturn(new PaymentMethodCard());

        given(paymentClient.getPayment("pg-key-1"))
                .willReturn(CompletableFuture.completedFuture(firstPayment));
        given(paymentClient.getPayment("pg-key-2"))
                .willReturn(CompletableFuture.completedFuture(secondPayment));
        given(paymentClient.cancelPayment(
                eq("pg-key-1"),
                eq(1000L),
                isNull(),
                isNull(),
                eq("CANCEL"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(),
                eq(1000L),
                isNull(),
                isNull(),
                isNull()
        )).willReturn(CompletableFuture.completedFuture(mock(CancelPaymentResponse.class)));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> mocked = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());

            portOneClient.getPayment("pg-key-1");
            portOneClient.getPayment("pg-key-2");
            portOneClient.cancelPayment(PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL"));

            assertThat(mocked.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("PortOne 결제 상태를 내부 상태 문자열로 변환한다")
    void getPayment_maps_portone_status() {
        assertPaymentStatus(mock(FailedPayment.class), "FAILED");
        assertPaymentStatus(mock(CancelledPayment.class), "CANCELLED");
        assertPaymentStatus(mock(PartialCancelledPayment.class), "PARTIAL_CANCELLED");
        assertPaymentStatus(mock(ReadyPayment.class), "READY");
        assertPaymentStatus(mock(PayPendingPayment.class), "PAY_PENDING");
        assertPaymentStatus(mock(VirtualAccountIssuedPayment.class), "VIRTUAL_ACCOUNT_ISSUED");
    }

    @Test
    @DisplayName("결제 금액이 없으면 0으로 처리한다")
    void getPayment_returns_zero_when_amount_is_null() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaidPayment paidPayment = mock(PaidPayment.class);

        given(paidPayment.getId()).willReturn("pg-key-no-amount");
        given(paidPayment.getAmount()).willReturn(null);
        given(paidPayment.getMethod()).willReturn(new PaymentMethodCard());
        given(paymentClient.getPayment("pg-key-no-amount"))
                .willReturn(CompletableFuture.completedFuture(paidPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-no-amount");
            assertThat(response.totalAmount()).isZero();
        }
    }

    @Test
    @DisplayName("간편결제 수단은 easyPay로 변환한다")
    void getPayment_maps_easyPay_method() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaidPayment paidPayment = mock(PaidPayment.class);
        PaymentAmount amount = new PaymentAmount(1000L, 0L, null, null, 0L, 1000L, 0L, 0L);

        given(paidPayment.getId()).willReturn("pg-key-easy-pay");
        given(paidPayment.getAmount()).willReturn(amount);
        given(paidPayment.getMethod()).willReturn(new PaymentMethodEasyPay());
        given(paymentClient.getPayment("pg-key-easy-pay"))
                .willReturn(CompletableFuture.completedFuture(paidPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-easy-pay");
            assertThat(response.payMethod()).isEqualTo("easyPay");
        }
    }

    @Test
    @DisplayName("알 수 없는 결제 수단은 unknown으로 변환한다")
    void getPayment_maps_unknown_method() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaidPayment paidPayment = mock(PaidPayment.class);
        PaymentAmount amount = new PaymentAmount(1000L, 0L, null, null, 0L, 1000L, 0L, 0L);

        given(paidPayment.getId()).willReturn("pg-key-unknown-method");
        given(paidPayment.getAmount()).willReturn(amount);
        given(paidPayment.getMethod()).willReturn(mock(PaymentMethod.class));
        given(paymentClient.getPayment("pg-key-unknown-method"))
                .willReturn(CompletableFuture.completedFuture(paidPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-unknown-method");
            assertThat(response.payMethod()).isEqualTo("unknown");
        }
    }

    @Test
    @DisplayName("인식할 수 없는 결제 응답이면 예외를 던진다")
    void getPayment_throws_when_payment_not_recognized() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        Payment unknownPayment = mock(Payment.class);
        given(paymentClient.getPayment("pg-key-unknown"))
                .willReturn(CompletableFuture.completedFuture(unknownPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            assertThatThrownBy(() -> portOneClient.getPayment("pg-key-unknown"))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Test
    @DisplayName("SDK getPayment 호출 실패 시 게이트웨이 예외로 변환한다")
    void getPayment_throws_when_sdk_call_fails() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        CompletableFuture<Payment> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("portone failed"));
        given(paymentClient.getPayment("pg-key-fail")).willReturn(failedFuture);

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            assertThatThrownBy(() -> portOneClient.getPayment("pg-key-fail"))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Test
    @DisplayName("SDK timeout 발생 시 게이트웨이 예외로 변환한다")
    void getPayment_throws_when_timeout_occurs() {
        PortOneProperties properties = createProperties();
        properties.setSdkTimeout(Duration.ofMillis(1));

        PaymentClient paymentClient = mock(PaymentClient.class);
        given(paymentClient.getPayment("pg-key-timeout"))
                .willReturn(new CompletableFuture<>());

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(properties);
            assertThatThrownBy(() -> portOneClient.getPayment("pg-key-timeout"))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Test
    @DisplayName("전액 취소 요청 시 취소 가능 금액을 동일하게 전달한다")
    void cancelPayment_full_calls_portone_with_same_cancellable_amount() {
        PaymentClient paymentClient = mock(PaymentClient.class);

        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL");

        given(paymentClient.cancelPayment(
                eq("pg-key-1"),
                eq(1000L),
                isNull(),
                isNull(),
                eq("CANCEL"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(),
                eq(1000L),
                isNull(),
                isNull(),
                isNull()
        )).willReturn(CompletableFuture.completedFuture(mock(CancelPaymentResponse.class)));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());

            assertThatCode(() -> portOneClient.cancelPayment(request))
                    .doesNotThrowAnyException();
        }

        verify(paymentClient).cancelPayment(
                eq("pg-key-1"), eq(1000L), isNull(), isNull(), eq("CANCEL"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(), eq(1000L), isNull(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("부분 취소 요청 시 현재 취소 가능 금액을 함께 전달한다")
    void cancelPayment_partial_calls_portone_with_different_cancellable_amount() {
        PaymentClient paymentClient = mock(PaymentClient.class);

        PaymentGatewayRequest request = PaymentGatewayRequest.ofPartial(
                "pg-key-partial", 2000L, 6000L, "CANCEL");

        given(paymentClient.cancelPayment(
                eq("pg-key-partial"),
                eq(2000L),
                isNull(),
                isNull(),
                eq("CANCEL"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(),
                eq(6000L),
                isNull(),
                isNull(),
                isNull()
        )).willReturn(CompletableFuture.completedFuture(mock(CancelPaymentResponse.class)));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());

            assertThatCode(() -> portOneClient.cancelPayment(request))
                    .doesNotThrowAnyException();
        }

        verify(paymentClient).cancelPayment(
                eq("pg-key-partial"), eq(2000L), isNull(), isNull(), eq("CANCEL"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(), eq(6000L), isNull(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("SDK cancelPayment 호출 실패 시 게이트웨이 예외로 변환한다")
    void cancelPayment_throws_when_sdk_call_fails() {
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL");
        CompletableFuture<CancelPaymentResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("portone cancel failed"));
        given(paymentClient.cancelPayment(
                anyString(), anyLong(), isNull(), isNull(), anyString(),
                any(CancelRequester.class), isNull(), anyLong(), isNull(), isNull(), isNull()
        )).willReturn(failedFuture);

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            assertThatThrownBy(() -> portOneClient.cancelPayment(request))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    private void assertPaymentStatus(Payment payment, String expectedStatus) {
        PaymentClient paymentClient = mock(PaymentClient.class);
        Payment.Recognized recognized = (Payment.Recognized) payment;
        PaymentAmount amount = new PaymentAmount(1000L, 0L, null, null, 0L, 1000L, 0L, 0L);

        given(recognized.getId()).willReturn("pg-key-" + expectedStatus);
        given(recognized.getAmount()).willReturn(amount);
        given(recognized.getMethod()).willReturn(new PaymentMethodCard());
        given(paymentClient.getPayment("pg-key-" + expectedStatus))
                .willReturn(CompletableFuture.completedFuture(payment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            PortOneClient portOneClient = new PortOneClient(createProperties());
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-" + expectedStatus);
            assertThat(response.status()).isEqualTo(expectedStatus);
        }
    }
}
