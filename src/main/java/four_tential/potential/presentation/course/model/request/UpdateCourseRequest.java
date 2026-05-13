package four_tential.potential.presentation.course.model.request;

import four_tential.potential.domain.course.course.CourseLevel;
import jakarta.validation.constraints.NotBlank;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public record UpdateCourseRequest(
        @NotBlank(message = "코스 제목을 입력해주세요") String title,
        @NotBlank(message = "코스 설명을 입력해주세요") String description,
        CourseLevel level,
        String addressMain,
        String addressDetail,
        BigInteger price,
        Integer capacity,
        LocalDateTime orderOpenAt,
        LocalDateTime orderCloseAt,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<@NotBlank String> imageUrls
) {
    public boolean hasPrepOnlyFields() {
        return price != null || capacity != null || level != null
                || addressMain != null || addressDetail != null
                || orderOpenAt != null || orderCloseAt != null
                || startAt != null || endAt != null;
    }
}
