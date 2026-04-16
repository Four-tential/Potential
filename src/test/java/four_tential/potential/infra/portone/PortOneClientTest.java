package four_tential.potential.infra.portone;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.payment.port.PaymentGatewayRequest;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import io.portone.sdk.server.payment.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
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
        return properties;
    }

    @Test
    @DisplayName("PortOneProperties 로 PortOneClient 를 생성할 수 있다")
    void createClient_with_properties() {
        PortOneClient portOneClient = new PortOneClient(createProperties());

        assertThat(portOneClient).isNotNull();
    }

    @Test
    @DisplayName("getPayment 호출 시 PortOne 결제 정보를 결제 게이트웨이 응답으로 변환한다")
    void getPayment_returns_gateway_response() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
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
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-1");

            assertThat(response.pgKey()).isEqualTo("pg-key-1");
            assertThat(response.status()).isEqualTo("PAID");
            assertThat(response.totalAmount()).isEqualTo(1000L);
            assertThat(response.payMethod()).isEqualTo("card");
        }
    }

    @Test
    @DisplayName("getPayment 호출 시 PortOne 결제 상태를 게이트웨이 상태 문자열로 변환한다")
    void getPayment_maps_portone_status() {
        assertPaymentStatus(mock(FailedPayment.class), "FAILED");
        assertPaymentStatus(mock(CancelledPayment.class), "CANCELLED");
        assertPaymentStatus(mock(PartialCancelledPayment.class), "PARTIAL_CANCELLED");
        assertPaymentStatus(mock(ReadyPayment.class), "READY");
        assertPaymentStatus(mock(PayPendingPayment.class), "PAY_PENDING");
        assertPaymentStatus(mock(VirtualAccountIssuedPayment.class), "VIRTUAL_ACCOUNT_ISSUED");
    }

    @Test
    @DisplayName("getPayment 호출 시 결제 금액이 없으면 0원으로 변환한다")
    void getPayment_returns_zero_when_amount_is_null() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
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
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-no-amount");

            assertThat(response.totalAmount()).isZero();
        }
    }

    @Test
    @DisplayName("getPayment 호출 시 간편 결제 수단은 easyPay 로 변환한다")
    void getPayment_maps_easyPay_method() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
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
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-easy-pay");

            assertThat(response.payMethod()).isEqualTo("easyPay");
        }
    }

    @Test
    @DisplayName("getPayment 호출 시 알 수 없는 결제 수단은 unknown 으로 변환한다")
    void getPayment_maps_unknown_method() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
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
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-unknown-method");

            assertThat(response.payMethod()).isEqualTo("unknown");
        }
    }

    @Test
    @DisplayName("getPayment 호출 시 PortOne 응답이 인식된 결제가 아니면 예외가 발생한다")
    void getPayment_throws_when_payment_not_recognized() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
        PaymentClient paymentClient = mock(PaymentClient.class);
        Payment unknownPayment = mock(Payment.class);
        given(paymentClient.getPayment("pg-key-unknown"))
                .willReturn(CompletableFuture.completedFuture(unknownPayment));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            assertThatThrownBy(() -> portOneClient.getPayment("pg-key-unknown"))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Test
    @DisplayName("getPayment 호출 중 PortOne SDK 예외가 발생하면 결제 게이트웨이 예외로 변환한다")
    void getPayment_throws_when_sdk_call_fails() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
        PaymentClient paymentClient = mock(PaymentClient.class);
        CompletableFuture<Payment> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("portone failed"));
        given(paymentClient.getPayment("pg-key-fail")).willReturn(failedFuture);

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            assertThatThrownBy(() -> portOneClient.getPayment("pg-key-fail"))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    @Test
    @DisplayName("cancelPayment 호출 시 PortOne 결제 취소 API를 호출한다")
    void cancelPayment_calls_portone_cancel_api() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL_REASON");
        given(paymentClient.cancelPayment(
                eq("pg-key-1"),
                eq(1000L),
                isNull(),
                isNull(),
                eq("CANCEL_REASON"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).willReturn(CompletableFuture.completedFuture(mock(CancelPaymentResponse.class)));

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            assertThatCode(() -> portOneClient.cancelPayment(request))
                    .doesNotThrowAnyException();
        }

        verify(paymentClient).cancelPayment(
                eq("pg-key-1"),
                eq(1000L),
                isNull(),
                isNull(),
                eq("CANCEL_REASON"),
                eq(CancelRequester.Customer.INSTANCE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("cancelPayment 호출 중 PortOne SDK 예외가 발생하면 결제 게이트웨이 예외로 변환한다")
    void cancelPayment_throws_when_sdk_call_fails() {
        PortOneClient portOneClient = new PortOneClient(createProperties());
        PaymentClient paymentClient = mock(PaymentClient.class);
        PaymentGatewayRequest request = PaymentGatewayRequest.of("pg-key-1", 1000L, "CANCEL_REASON");
        CompletableFuture<CancelPaymentResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("portone cancel failed"));
        given(paymentClient.cancelPayment(
                anyString(),
                anyLong(),
                isNull(),
                isNull(),
                anyString(),
                any(CancelRequester.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).willReturn(failedFuture);

        try (MockedConstruction<io.portone.sdk.server.PortOneClient> ignored = mockConstruction(
                io.portone.sdk.server.PortOneClient.class,
                (mock, context) -> given(mock.getPayment()).willReturn(paymentClient)
        )) {
            assertThatThrownBy(() -> portOneClient.cancelPayment(request))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }

    private void assertPaymentStatus(Payment payment, String expectedStatus) {
        PortOneClient portOneClient = new PortOneClient(createProperties());
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
            PaymentGatewayResponse response = portOneClient.getPayment("pg-key-" + expectedStatus);

            assertThat(response.status()).isEqualTo(expectedStatus);
        }
    }
}
