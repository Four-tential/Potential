package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.CourseCancelOutbox;
import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseCancelOutboxRepository extends JpaRepository<CourseCancelOutbox, UUID> {

    List<CourseCancelOutbox> findByStatus(CourseCancelOutboxStatus status);
}
