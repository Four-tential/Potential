package four_tential.potential.domain.course.course_category;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "course_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_categories_code", columnNames = {"code"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseCategory extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    public static CourseCategory register(String code, String name) {
        CourseCategory category = new CourseCategory();
        category.code = code;
        category.name = name;
        return category;
    }
}
