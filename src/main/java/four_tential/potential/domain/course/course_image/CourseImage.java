package four_tential.potential.domain.course.course_image;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "course_image")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CourseImage extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "image_url", nullable = false, length = 300)
    private String imageUrl;

    public static CourseImage register(UUID courseId, String imageUrl) {
        CourseImage image = new CourseImage();
        image.courseId = courseId;
        image.imageUrl = imageUrl;
        return image;
    }
}
