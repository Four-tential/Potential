package four_tential.potential.domain.course.course_wishlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseWishlistRepository extends JpaRepository<CourseWishlist, UUID>, CourseWishlistQueryRepository {

    boolean existsByMemberIdAndCourseId(UUID memberId, UUID courseId);

    Optional<CourseWishlist> findByMemberIdAndCourseId(UUID memberId, UUID courseId);

    @Query("SELECT cw.courseId FROM CourseWishlist cw WHERE cw.memberId = :memberId AND cw.courseId IN :courseIds")
    List<UUID> findWishlistedCourseIds(@Param("memberId") UUID memberId, @Param("courseIds") Collection<UUID> courseIds);

    void deleteByCourseId(UUID courseId);
}
