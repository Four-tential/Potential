package four_tential.potential.domain.payment.repository;

import four_tential.potential.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPgKey(String pgKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.pgKey = :pgKey")
    Optional<Payment> findByPgKeyForUpdate(@Param("pgKey") String pgKey);

    boolean existsByOrderId(UUID orderId);
}
