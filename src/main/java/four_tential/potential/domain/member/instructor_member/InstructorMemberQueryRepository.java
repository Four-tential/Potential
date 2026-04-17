package four_tential.potential.domain.member.instructor_member;

import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationDetail;
import four_tential.potential.presentation.instructor_member.model.response.InstructorApplicationItem;
import four_tential.potential.presentation.instructor_member.model.response.MyInstructorApplicationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface InstructorMemberQueryRepository {
    Page<InstructorApplicationItem> findInstructorApplications(InstructorMemberStatus status, Pageable pageable);
    Optional<InstructorApplicationDetail> findInstructorApplicationDetail(UUID memberId);
    Optional<MyInstructorApplicationResponse> findMyInstructorApplication(UUID memberId);
}
