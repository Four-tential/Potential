package four_tential.potential.presentation.course.model.request;

import four_tential.potential.domain.course.course.CourseLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public record CreateCourseRequestRequest(
        @NotBlank(message = "제목을 입력해주세요")
        @Size(max = 100, message = "제목은 100자 이하로 입력해주세요")
        String title,

        @NotBlank(message = "설명을 입력해주세요")
        String description,

        @NotBlank(message = "주소를 입력해주세요")
        @Size(max = 300, message = "주소는 300자 이하로 입력해주세요")
        String addressMain,

        @NotBlank(message = "상세 주소를 입력해주세요")
        @Size(max = 300, message = "상세 주소는 300자 이하로 입력해주세요")
        String addressDetail,

        @NotNull(message = "가격을 입력해주세요")
        BigInteger price,

        @Min(value = 1, message = "정원은 최소 1명 이상이어야 합니다")
        int capacity,

        @NotNull(message = "주문 시작 시간을 입력해주세요")
        @Future(message = "주문 시작 시간은 현재보다 미래여야 합니다")
        LocalDateTime orderOpenAt,

        @NotNull(message = "주문 마감 시간을 입력해주세요")
        @Future(message = "주문 마감 시간은 현재보다 미래여야 합니다")
        LocalDateTime orderCloseAt,

        @NotNull(message = "코스 시작 시간을 입력해주세요")
        @Future(message = "코스 시작 시간은 현재보다 미래여야 합니다")
        LocalDateTime startAt,

        @NotNull(message = "코스 종료 시간을 입력해주세요")
        @Future(message = "코스 종료 시간은 현재보다 미래여야 합니다")
        LocalDateTime endAt,

        @NotNull(message = "난이도를 입력해주세요")
        CourseLevel level,

        List<String> imageUrls
) {
}
