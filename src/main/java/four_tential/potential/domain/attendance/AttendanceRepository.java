package four_tential.potential.domain.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID>, AttendanceRepositoryCustom {

    @Modifying
    @Query("delete from Attendance a where a.memberId in :memberIds")
    void deleteByMemberIdIn(@Param("memberIds") Collection<UUID> memberIds);

    @Modifying
    @Query("delete from Attendance a where a.courseId in :courseIds")
    void deleteByCourseIdIn(@Param("courseIds") Collection<UUID> courseIds);
}