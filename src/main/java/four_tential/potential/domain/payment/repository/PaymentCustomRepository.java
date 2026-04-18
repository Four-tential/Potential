package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.presentation.payment.dto.PaymentDetailResponse;
import four_tential.potential.presentation.payment.dto.PaymentListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PaymentCustomRepository {

    Optional<Payment> findByPgKey(String pgKey);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByPgKeyForUpdate(String pgKey);

    boolean existsByOrderId(UUID orderId);

    Optional<PaymentDetailResponse> findDetailByIdAndMemberId(UUID paymentId, UUID memberId);

    Page<PaymentListResponse> findListByMemberIdAndStatus(UUID memberId, PaymentStatus status, Pageable pageable);
}
