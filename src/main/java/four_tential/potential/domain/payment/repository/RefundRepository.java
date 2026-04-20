package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Query("""
            select coalesce(sum(r.refundPrice), 0)
            from Refund r
            where r.payment.id = :paymentId
              and r.status = :status
            """)
    Long sumRefundPriceByPaymentIdAndStatus(
            @Param("paymentId") UUID paymentId,
            @Param("status") RefundStatus status
    );
}
