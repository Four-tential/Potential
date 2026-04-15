package four_tential.potential.domain.course.course_image;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.course.course.Course;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "course_images")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CourseImage extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "image_url", nullable = false, length = 300)
    private String imageUrl;

    public static CourseImage register(Course course, String imageUrl) {
        CourseImage image = new CourseImage();
        image.course = course;
        image.imageUrl = imageUrl;
        return image;
    }
}
