package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * PortOne 결제 식별자인 pgKey로 Payment를 조회한다.
     * 결제가 반드시 있어야 하는 흐름에서 사용하며, 없으면 결제 없음 예외를 던진다.
     */
    public Payment getByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    /**
     * PortOne 결제 식별자인 pgKey로 Payment를 Optional 형태로 조회한다.
     * Paid 웹훅이 결제 생성보다 먼저 도착할 수 있는 흐름처럼, 결제가 없을 수도 있는 경우에 사용한다.
     */
    public Optional<Payment> findByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey);
    }

    /**
     * Payment row에 비관적 락을 걸고 조회한다.
     * 같은 결제에 대한 웹훅이 동시에 처리되어 상태가 중복 변경되는 상황을 방지한다.
     */
    public Payment getByPgKeyForUpdate(String pgKey) {
        return paymentRepository.findByPgKeyForUpdate(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    /**
     * Payment row에 비관적 락을 걸고 Optional 형태로 조회한다.
     * 실패/취소 웹훅처럼 결제 row가 아직 없을 수 있는 흐름에서 사용한다.
     */
    public Optional<Payment> findByPgKeyForUpdate(String pgKey) {
        return paymentRepository.findByPgKeyForUpdate(pgKey);
    }

    /**
     * 하나의 주문에 이미 결제가 생성되어 있는지 확인한다.
     * 결제가 존재하면 같은 주문으로 결제를 다시 만들 수 없도록 예외를 던진다.
     */
    public void validateNoPayment(UUID orderId) {
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_REQUESTED);
        }
    }

    /**
     * PortOne 실제 결제 정보와 서버가 계산한 결제 정보를 비교한다.
     * 결제 수단, pgKey, 결제 완료 상태, 결제 금액이 모두 맞아야 결제 생성을 허용한다.
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
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_KEY_MISMATCH);
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

    /**
     * Payment 엔티티를 저장한다.
     * 이미 생성된 Payment 객체를 그대로 저장해야 하는 테스트나 내부 흐름에서 사용한다.
     */
    @Transactional
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    /**
     * 검증이 끝난 PaymentCreateCommand 값으로 PENDING 결제를 생성한다.
     * 결제 완료 확정은 PortOne Paid 웹훅에서 처리되므로 여기서는 결제 요청 기록만 저장한다.
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

    /**
     * Payment 엔티티를 PAID 상태로 변경한다.
     * 상태 전이 가능 여부는 Payment 엔티티 내부의 도메인 규칙이 판단한다.
     * getByPgKeyForUpdate()로 조회한 managed entity를 기존 트랜잭션 안에서 넘겨야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPaid(Payment payment) {
        payment.confirmPaid();
    }

    /**
     * Payment 엔티티를 FAILED 상태로 변경한다.
     * PortOne 실패/취소 웹훅 또는 서버 검증 실패 후 결제 실패로 확정해야 할 때 사용한다.
     * getByPgKeyForUpdate()로 조회한 managed entity를 기존 트랜잭션 안에서 넘겨야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fail(Payment payment) {
        payment.fail();
    }
}
