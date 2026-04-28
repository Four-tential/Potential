package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.RefundOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundOutboxRepository extends JpaRepository<RefundOutbox,Long> {
}
