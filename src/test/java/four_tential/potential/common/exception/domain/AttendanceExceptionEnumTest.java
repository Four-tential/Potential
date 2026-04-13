package four_tential.potential.common.exception.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceExceptionEnumTest {

    @Nested
    @DisplayName("enum 전체 값 검증")
    class EnumValuesTest {

        @Test
        @DisplayName("총 12개의 에러 코드가 정의되어 있다")
        void enum_hasTwelveValues() {
            assertThat(AttendanceExceptionEnum.values()).hasSize(12);
        }

        @ParameterizedTest
        @EnumSource(AttendanceExceptionEnum.class)
        @DisplayName("모든 에러 코드는 httpStatus 가 null 이 아니다")
        void allEnums_httpStatusIsNotNull(AttendanceExceptionEnum e) {
            assertThat(e.getHttpStatus()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(AttendanceExceptionEnum.class)
        @DisplayName("모든 에러 코드는 message 가 null 이 아니고 비어있지 않다")
        void allEnums_messageIsNotBlank(AttendanceExceptionEnum e) {
            assertThat(e.getMessage()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("QR 생성 관련 에러")
    class QrGenerationErrorTest {

        @Test
        @DisplayName("ERR_QR_FORBIDDEN 은 403 FORBIDDEN 이다")
        void errQrForbidden() {
            assertThat(AttendanceExceptionEnum.ERR_QR_FORBIDDEN.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("ERR_QR_NOT_STARTED 는 403 FORBIDDEN 이다")
        void errQrNotStarted() {
            assertThat(AttendanceExceptionEnum.ERR_QR_NOT_STARTED.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("ERR_QR_EXPIRED_WINDOW 는 403 FORBIDDEN 이다")
        void errQrExpiredWindow() {
            assertThat(AttendanceExceptionEnum.ERR_QR_EXPIRED_WINDOW.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("ERR_QR_ALREADY_ACTIVE 는 409 CONFLICT 이다")
        void errQrAlreadyActive() {
            assertThat(AttendanceExceptionEnum.ERR_QR_ALREADY_ACTIVE.getHttpStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("QR 스캔 관련 에러")
    class QrScanErrorTest {

        @Test
        @DisplayName("ERR_QR_INVALID_FORMAT 은 400 BAD_REQUEST 이다")
        void errQrInvalidFormat() {
            assertThat(AttendanceExceptionEnum.ERR_QR_INVALID_FORMAT.getHttpStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("ERR_QR_NOT_FOUND 는 404 NOT_FOUND 이다")
        void errQrNotFound() {
            assertThat(AttendanceExceptionEnum.ERR_QR_NOT_FOUND.getHttpStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("ERR_QR_EXPIRED 는 408 REQUEST_TIMEOUT 이다")
        void errQrExpired() {
            assertThat(AttendanceExceptionEnum.ERR_QR_EXPIRED.getHttpStatus())
                    .isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        }

        @Test
        @DisplayName("ERR_ALREADY_CHECKED 는 409 CONFLICT 이다")
        void errAlreadyChecked() {
            assertThat(AttendanceExceptionEnum.ERR_ALREADY_CHECKED.getHttpStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("출석 권한 관련 에러")
    class AttendanceAuthErrorTest {

        @Test
        @DisplayName("ERR_ORDER_NOT_CONFIRMED 는 403 FORBIDDEN 이다")
        void errOrderNotConfirmed() {
            assertThat(AttendanceExceptionEnum.ERR_ORDER_NOT_CONFIRMED.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("ERR_NOT_ENROLLED 는 403 FORBIDDEN 이다")
        void errNotEnrolled() {
            assertThat(AttendanceExceptionEnum.ERR_NOT_ENROLLED.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("ERR_ATTENDANCE_FORBIDDEN 은 403 FORBIDDEN 이다")
        void errAttendanceForbidden() {
            assertThat(AttendanceExceptionEnum.ERR_ATTENDANCE_FORBIDDEN.getHttpStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("출석 조회 관련 에러")
    class AttendanceQueryErrorTest {

        @Test
        @DisplayName("ERR_NOT_FOUND_ATTENDANCE 는 404 NOT_FOUND 이다")
        void errNotFoundAttendance() {
            assertThat(AttendanceExceptionEnum.ERR_NOT_FOUND_ATTENDANCE.getHttpStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("ServiceErrorCode 인터페이스 구현 검증")
    class ServiceErrorCodeInterfaceTest {

        @ParameterizedTest
        @EnumSource(AttendanceExceptionEnum.class)
        @DisplayName("모든 enum 은 ServiceErrorCode 를 구현한다")
        void allEnums_implementServiceErrorCode(AttendanceExceptionEnum e) {
            assertThat(e).isInstanceOf(four_tential.potential.common.exception.ServiceErrorCode.class);
        }

        @Test
        @DisplayName("valueOf 로 enum 상수를 정상적으로 가져올 수 있다")
        void valueOf_works() {
            assertThat(AttendanceExceptionEnum.valueOf("ERR_QR_FORBIDDEN"))
                    .isEqualTo(AttendanceExceptionEnum.ERR_QR_FORBIDDEN);
        }
    }
}
