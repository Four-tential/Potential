package four_tential.potential.domain.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findAllByCourseId(UUID courseId);

    Optional<Attendance> findByMemberIdAndCourseId(UUID memberId, UUID courseId);

    boolean existsByMemberIdAndCourseIdAndStatus(UUID memberId, UUID courseId, AttendanceStatus status);
}