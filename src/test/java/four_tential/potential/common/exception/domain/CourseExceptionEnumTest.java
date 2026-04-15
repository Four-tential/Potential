package four_tential.potential.common.exception.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CourseExceptionEnumTest {

    @Test
    @DisplayName("코스 관련 예외 Enum이 올바른 메시지와 상태코드를 가집니다")
    void checkCourseExceptionEnum() {
        for (CourseExceptionEnum exceptionEnum : CourseExceptionEnum.values()) {
            assertThat(exceptionEnum.getHttpStatus()).isNotNull();
            assertThat(exceptionEnum.getMessage()).isNotBlank();
        }
    }

    @Test
    @DisplayName("특정 예외 케이스의 값을 검증합니다")
    void validateSpecificException() {
        assertThat(CourseExceptionEnum.INVALID_CAPACITY.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(CourseExceptionEnum.INVALID_CAPACITY.getMessage()).isEqualTo("코스의 정원은 최소 1명 이상이어야 합니다");
    }
}
