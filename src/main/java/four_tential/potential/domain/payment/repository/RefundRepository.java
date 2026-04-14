package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
}
