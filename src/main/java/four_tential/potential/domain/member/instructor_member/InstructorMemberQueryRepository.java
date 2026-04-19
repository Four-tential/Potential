package four_tential.potential.domain.member.instructor_member;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface InstructorMemberQueryRepository {
    Page<InstructorApplicationItemResult> findInstructorApplications(InstructorMemberStatus status, Pageable pageable);
    Optional<InstructorApplicationDetailResult> findInstructorApplicationDetail(UUID memberId);
    Optional<MyInstructorApplicationResult> findMyInstructorApplication(UUID memberId);
}
