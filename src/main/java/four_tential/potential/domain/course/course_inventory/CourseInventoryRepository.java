package four_tential.potential.domain.course.course_inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseInventoryRepository extends JpaRepository<CourseInventory, UUID> {

    /**
     * 비관적 쓰기 락으로 인벤토리를 조회한다.
     * 결제 확정(웹훅 처리) 시점에 동시 수정을 방지하기 위해 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CourseInventory ci WHERE ci.courseId = :courseId")
    Optional<CourseInventory> findByCourseIdForUpdate(@Param("courseId") UUID courseId);
}
