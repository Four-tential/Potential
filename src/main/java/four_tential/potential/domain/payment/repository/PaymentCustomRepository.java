package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;

import java.util.Optional;
import java.util.UUID;

public interface PaymentCustomRepository {

    Optional<Payment> findByPgKey(String pgKey);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByPgKeyForUpdate(String pgKey);

    boolean existsByOrderId(UUID orderId);

    Optional<PaymentDetailResponse> findDetailByIdAndMemberId(UUID paymentId, UUID memberId);
}
