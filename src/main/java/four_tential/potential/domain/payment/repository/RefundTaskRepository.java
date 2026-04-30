package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundTaskRepository extends JpaRepository<RefundTask, UUID> {

    List<RefundTask> findByStatus(RefundTaskStatus status);

    boolean existsByStatus(RefundTaskStatus status);
}
