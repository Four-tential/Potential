package four_tential.potential.domain.member.follow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID>, FollowQueryRepository {
    boolean existsByMemberIdAndMemberInstructorId(UUID memberId, UUID memberInstructorId);

    Optional<Follow> findByMemberIdAndMemberInstructorId(UUID memberId, UUID memberInstructorId);
}
