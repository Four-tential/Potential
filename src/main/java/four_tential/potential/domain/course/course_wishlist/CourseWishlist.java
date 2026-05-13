package four_tential.potential.domain.course.course_wishlist;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "course_wishlists", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_wishlists_member_course", columnNames = {"member_id", "course_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseWishlist extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    public static CourseWishlist register(UUID memberId, UUID courseId) {
        CourseWishlist wishlist = new CourseWishlist();
        wishlist.memberId = memberId;
        wishlist.courseId = courseId;
        return wishlist;
    }
}
