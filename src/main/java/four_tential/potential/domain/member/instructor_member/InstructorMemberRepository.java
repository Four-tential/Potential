package four_tential.potential.domain.member.instructor_member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstructorMemberRepository extends JpaRepository<InstructorMember, UUID>, InstructorMemberQueryRepository {
    Optional<InstructorMember> findByMemberId(UUID memberId);

    boolean existsByCategoryCode(String categoryCode);
}
