package four_tential.potential.application.payment;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.PaymentExceptionEnum;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * pgKey 로 결제 조회
     *
     * @param pgKey PortOne 결제 식별자
     * @return Payment 엔티티
     */
    public Payment getByPgKey(String pgKey) {
        return paymentRepository.findByPgKey(pgKey).orElseThrow(
                () -> new ServiceErrorException(PaymentExceptionEnum.ERR_NOT_FOUND_PAYMENT));
    }

    /**
     * 결제 저장
     */
    @Transactional
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    /**
     * 결제 확정 처리
     * 웹훅 Transaction.Paid 수신 시 호출
     * pgKey 로 Payment 를 찾아서 PAID 상태로 변경
     *
     * @param pgKey PortOne 결제 식별자
     */
    @Transactional
    public void confirmPaid(String pgKey) {
        Payment payment = getByPgKey(pgKey);
        payment.confirmPaid(pgKey);
    }

    /**
     * 결제 실패 처리
     * 웹훅 Transaction.Failed 수신 시 호출
     *
     * @param pgKey PortOne 결제 식별자
     */
    @Transactional
    public void fail(String pgKey) {
        Payment payment = getByPgKey(pgKey);
        payment.fail();
    }
}
