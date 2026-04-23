package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "초급자를 위한 하타 요가") String title,
        @Schema(example = "기초 동작부터 차근차근 배우는 하타 요가 클래스입니다.") String description,
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "요가") String categoryName,
        CourseDetailInstructorInfo instructor,
        @Schema(example = "[\"https://cdn.example.com/course-image/a1b2c3d4/img1.jpg\"]") List<String> images,
        @Schema(example = "서울시 강남구 테헤란로 123") String addressMain,
        @Schema(example = "2층 요가 스튜디오") String addressDetail,
        @Schema(example = "120000") BigInteger price,
        @Schema(example = "20") int capacity,
        @Schema(example = "15") int confirmCount,
        @Schema(example = "OPEN") CourseStatus status,
        @Schema(example = "BEGINNER") CourseLevel level,
        @Schema(example = "2025-05-01T10:00:00") LocalDateTime orderOpenAt,
        @Schema(example = "2025-05-20T23:59:59") LocalDateTime orderCloseAt,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime startAt,
        @Schema(example = "2025-06-30T18:00:00") LocalDateTime endAt,
        @Schema(example = "4.7") double averageRating,
        @Schema(example = "23") long reviewCount,
        @Schema(example = "false") boolean isWishlisted
) {
}
