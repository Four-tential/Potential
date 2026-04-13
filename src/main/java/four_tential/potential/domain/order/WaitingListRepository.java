package four_tential.potential.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WaitingListRepository extends JpaRepository<WaitingList, UUID> {
}
