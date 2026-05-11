package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.RefundTask;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RefundTaskRepository extends JpaRepository<RefundTask, UUID> {

    List<RefundTask> findByStatus(RefundTaskStatus status);

    boolean existsByStatus(RefundTaskStatus status);

    boolean existsByStatusAndNextRetryAtLessThanEqual(RefundTaskStatus status, LocalDateTime now);

    @Query("""
            SELECT t FROM RefundTask t
            WHERE t.status = 'RETRY_PENDING'
              AND t.nextRetryAt <= :now
            """)
    List<RefundTask> findRetryPendingBefore(@Param("now") LocalDateTime now);
}
