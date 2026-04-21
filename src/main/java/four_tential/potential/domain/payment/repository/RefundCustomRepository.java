package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.presentation.payment.dto.RefundDetailResponse;

import java.util.Optional;
import java.util.UUID;

public interface RefundCustomRepository {
    /**
     * 특정 결제의 특정 상태 환불 금액 합계를 조회
     * 환불 이력이 없으면 0을 반환한다.
     */
    Long sumRefundPriceByPaymentIdAndStatus(UUID paymentId, RefundStatus status);

    Optional<RefundDetailResponse> findDetailByIdAndMemberId(UUID refundId, UUID memberId);
}
