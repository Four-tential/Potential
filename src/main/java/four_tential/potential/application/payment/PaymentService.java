package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     *  PortOne 결제 식별자인 pgKey로 Payment를 조회
     */
    public Payment getByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    public Optional<Payment> findByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey);
    }

    /**
     * Payment row에 비관적 락을 걸고 조회해 같은 결제가 동시에 수정되는 것 방지
     */
    public Payment getByPgKeyForUpdate(String pgKey) {
        return paymentRepository.findByPgKeyForUpdate(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    /**
     * 하나의 주문에는 결제가 하나만 생성되어야 함
     */
    public void validateNoPayment(UUID orderId) {
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_REQUESTED);
        }
    }

    /**
     * PortOne 실제 결제 정보와 서버가 계산한 결제 정보가 일치하는지 확인
     */
    public void validateGatewayPayment(
            PaymentCreateCommand preparation,
            String pgKey,
            PaymentPayWay requestPayWay,
            PaymentGatewayResponse gatewayResponse
    ) {
        if (requestPayWay != PaymentPayWay.CARD) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_METHOD_NOT_ALLOWED);
        }
        if (!pgKey.equals(gatewayResponse.pgKey())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
        if (!"PAID".equals(gatewayResponse.status())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_NOT_PAID);
        }
        if (!"card".equals(gatewayResponse.payMethod())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_METHOD_NOT_ALLOWED);
        }
        if (!preparation.paidTotalPrice().equals(gatewayResponse.totalAmount())) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    @Transactional
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    /**
     * 검증이 끝난 PaymentCreatePreparation 값으로 PENDING 결제를 생성
     * 결제 완료 확정은 웹훅에서 처리되므로 여기서는 PENDING 상태로 저장
     */
    @Transactional
    public Payment createPendingPayment(PaymentCreateCommand preparation, String pgKey, PaymentPayWay payWay) {
        Payment payment = Payment.createPending(
                preparation.orderId(),
                preparation.memberId(),
                preparation.memberCouponId(),
                pgKey,
                preparation.totalPrice(),
                preparation.discountPrice(),
                preparation.paidTotalPrice(),
                payWay
        );

        return paymentRepository.save(payment);
    }

    public void confirmPaid(Payment payment) {
        payment.confirmPaid();
    }

    public void fail(Payment payment) {
        payment.fail();
    }
}
