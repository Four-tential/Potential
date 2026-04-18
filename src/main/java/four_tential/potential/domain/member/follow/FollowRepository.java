package four_tential.potential.domain.member.follow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    boolean existsByMemberIdAndMemberInstructorId(UUID memberId, UUID memberInstructorId);
}
