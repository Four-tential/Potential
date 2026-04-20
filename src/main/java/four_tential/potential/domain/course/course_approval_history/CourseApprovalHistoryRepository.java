package four_tential.potential.domain.course.course_approval_history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseApprovalHistoryRepository extends JpaRepository<CourseApprovalHistory, UUID> {
}
