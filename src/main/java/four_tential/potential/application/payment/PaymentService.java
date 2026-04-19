package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.port.PaymentGatewayResponse;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * pgKey로 Payment를 조회한다
     * 결제가 꼭 있어야 하는 흐름에서 사용한다
     */
    public Payment getByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    /**
     * pgKey로 Payment를 조회하되, 없을 수도 있는 흐름에서 사용한다
     */
    public Optional<Payment> findByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey);
    }

    /**
     * 주문 ID로 이미 생성된 Payment를 조회한다
     * 같은 주문의 중복 결제를 막기 위해 사용한다
     */
    public Optional<Payment> findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /**
     * Payment row를 쓰기 락으로 잡고 조회한다
     * 같은 결제의 웹훅들이 동시에 상태를 바꾸지 못하게 한다
     */
    public Payment getByPgKeyForUpdate(String pgKey) {
        return paymentRepository.findByPgKeyForUpdate(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    /**
     * 쓰기 락으로 조회하되, 결제가 없을 수도 있는 웹훅 흐름에서 사용한다
     */
    public Optional<Payment> findByPgKeyForUpdate(String pgKey) {
        return paymentRepository.findByPgKeyForUpdate(pgKey);
    }

    /**
     * 주문 하나에 결제 흐름이 하나만 생기도록 막는다
     */
    public void validateNoPayment(UUID orderId) {
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_ALREADY_REQUESTED);
        }
    }

    /**
     * PortOne 조회 결과와 우리 서버의 주문 금액을 대조한다
     * 클라이언트 값은 믿지 않고, pgKey/상태/수단/금액이 모두 맞을 때만 통과시킨다
     */
    public void validateGatewayPayment(
            PaymentCreateCommand preparation,
            String pgKey,
            PaymentPayWay requestPayWay,
            PaymentGatewayResponse gatewayResponse
    ) {
        if (requestPayWay != PaymentPayWay.CARD) {
            log.warn("[PAYMENT_VALIDATE] 허용되지 않는 결제 수단 요청. requestPayWay={}", requestPayWay);
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_METHOD_NOT_ALLOWED);
        }
        if (!pgKey.equals(gatewayResponse.pgKey())) {
            log.error("[PAYMENT_VALIDATE] pgKey 불일치. request={} gateway={}", pgKey, gatewayResponse.pgKey());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_KEY_MISMATCH);
        }
        if (!"PAID".equals(gatewayResponse.status())) {
            log.warn("[PAYMENT_VALIDATE] PortOne 미결제 상태. pgKey={} status={}", pgKey, gatewayResponse.status());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_NOT_PAID);
        }
        if (!"card".equals(gatewayResponse.payMethod())) {
            log.warn("[PAYMENT_VALIDATE] PortOne 결제 수단 불일치. pgKey={} payMethod={}", pgKey, gatewayResponse.payMethod());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_METHOD_NOT_ALLOWED);
        }
        if (!preparation.paidTotalPrice().equals(gatewayResponse.totalAmount())) {
            log.error("[PAYMENT_VALIDATE] 금액 불일치. pgKey={} expected={} actual={}",
                    pgKey, preparation.paidTotalPrice(), gatewayResponse.totalAmount());
            throw new ServiceErrorException(PaymentExceptionEnum.ERR_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    /**
     * 이미 만들어진 Payment를 그대로 저장한다
     */
    @Transactional
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    /**
     * 서버 검증을 통과한 결제 요청을 PENDING으로 저장한다
     * 최종 PAID 확정은 서명 검증된 Paid 웹훅에서 처리한다
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
     * 실패한 결제 시도를 FAILED로 저장한다
     * 운영에서 "시도조차 없던 주문"과 "실패한 결제 시도"를 구분하기 위함이다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createFailedPayment(PaymentCreateCommand preparation, String pgKey, PaymentPayWay payWay) {
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
        payment.fail();

        return paymentRepository.save(payment);
    }

    /**
     * Payment를 PAID로 확정한다
     * 락으로 조회한 현재 보고 있는 entity를 기존 트랜잭션 안에서 넘겨야 한다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPaid(Payment payment) {
        payment.confirmPaid();
    }

    /**
     * Payment를 FAILED로 확정한다
     * confirmPaid와 마찬가지로 기존 트랜잭션 안에서 호출해야 한다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fail(Payment payment) {
        payment.fail();
    }

    public Payment getById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }
}
