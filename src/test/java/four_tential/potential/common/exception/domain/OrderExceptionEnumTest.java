package four_tential.potential.common.exception.domain;

import four_tential.potential.common.exception.ServiceErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderExceptionEnum")
class OrderExceptionEnumTest {

    @Test
    @DisplayName("OrderExceptionEnum은 ServiceErrorCode 인터페이스를 구현한다")
    void implementsServiceErrorCode() {
        assertThat(OrderExceptionEnum.ERR_NOT_FOUND_ORDER).isInstanceOf(ServiceErrorCode.class);
    }

    @Test
    @DisplayName("ERR_NOT_FOUND_ORDER는 HTTP 404 상태를 가진다")
    void errNotFoundOrderHasHttpStatus404() {
        assertThat(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ERR_NOT_FOUND_ORDER는 올바른 메시지를 가진다")
    void errNotFoundOrderHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getMessage()).isEqualTo("주문 정보를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("ERR_ALREADY_RESERVED는 HTTP 409 상태를 가진다")
    void errAlreadyReservedHasHttpStatus409() {
        assertThat(OrderExceptionEnum.ERR_ALREADY_RESERVED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("ERR_ALREADY_RESERVED는 올바른 메시지를 가진다")
    void errAlreadyReservedHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_ALREADY_RESERVED.getMessage()).isEqualTo("동일한 시간대에 이미 예약된 코스가 있습니다");
    }

    @Test
    @DisplayName("ERR_NO_AVAILABLE_SEATS는 HTTP 400 상태를 가진다")
    void errNoAvailableSeatsHasHttpStatus400() {
        assertThat(OrderExceptionEnum.ERR_NO_AVAILABLE_SEATS.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_NO_AVAILABLE_SEATS는 올바른 메시지를 가진다")
    void errNoAvailableSeatsHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_NO_AVAILABLE_SEATS.getMessage()).isEqualTo("코스의 잔여석이 없습니다");
    }

    @Test
    @DisplayName("ERR_INVALID_ORDER_STATUS는 HTTP 400 상태를 가진다")
    void errInvalidOrderStatusHasHttpStatus400() {
        assertThat(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_INVALID_ORDER_STATUS는 올바른 메시지를 가진다")
    void errInvalidOrderStatusHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS.getMessage()).isEqualTo("변경할 수 없는 주문 상태입니다");
    }

    @Test
    @DisplayName("ERR_CANNOT_CANCEL_ORDER는 HTTP 400 상태를 가진다")
    void errCannotCancelOrderHasHttpStatus400() {
        assertThat(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_CANNOT_CANCEL_ORDER는 올바른 메시지를 가진다")
    void errCannotCancelOrderHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER.getMessage()).isEqualTo("코스 시작 7일 전까지만 취소가 가능합니다");
    }

    @Test
    @DisplayName("ERR_ORDER_EXPIRED는 HTTP 400 상태를 가진다")
    void errOrderExpiredHasHttpStatus400() {
        assertThat(OrderExceptionEnum.ERR_ORDER_EXPIRED.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ERR_ORDER_EXPIRED는 올바른 메시지를 가진다")
    void errOrderExpiredHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_ORDER_EXPIRED.getMessage()).isEqualTo("결제 가능 시간이 초과되었습니다");
    }

    @Test
    @DisplayName("ERR_QUEUE_FULL는 HTTP 503 상태를 가진다")
    void errQueueFullHasHttpStatus503() {
        assertThat(OrderExceptionEnum.ERR_QUEUE_FULL.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("ERR_QUEUE_FULL는 올바른 메시지를 가진다")
    void errQueueFullHasCorrectMessage() {
        assertThat(OrderExceptionEnum.ERR_QUEUE_FULL.getMessage()).isEqualTo("현재 대기열이 가득 차서 신청할 수 없습니다");
    }

    @Test
    @DisplayName("OrderExceptionEnum은 정확히 7개의 값을 가진다")
    void hasExactlySevenValues() {
        assertThat(OrderExceptionEnum.values()).hasSize(7);
    }

    @ParameterizedTest
    @EnumSource(OrderExceptionEnum.class)
    @DisplayName("모든 OrderExceptionEnum 값은 null이 아닌 HTTP 상태를 가진다")
    void allEnumValuesHaveNonNullHttpStatus(OrderExceptionEnum errorEnum) {
        assertThat(errorEnum.getHttpStatus()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(OrderExceptionEnum.class)
    @DisplayName("모든 OrderExceptionEnum 값은 null이 아닌 메시지를 가진다")
    void allEnumValuesHaveNonNullMessage(OrderExceptionEnum errorEnum) {
        assertThat(errorEnum.getMessage()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("이름으로 OrderExceptionEnum 값을 조회할 수 있다")
    void canLookUpValuesByName() {
        assertThat(OrderExceptionEnum.valueOf("ERR_NOT_FOUND_ORDER")).isEqualTo(OrderExceptionEnum.ERR_NOT_FOUND_ORDER);
        assertThat(OrderExceptionEnum.valueOf("ERR_ALREADY_RESERVED")).isEqualTo(OrderExceptionEnum.ERR_ALREADY_RESERVED);
        assertThat(OrderExceptionEnum.valueOf("ERR_NO_AVAILABLE_SEATS")).isEqualTo(OrderExceptionEnum.ERR_NO_AVAILABLE_SEATS);
        assertThat(OrderExceptionEnum.valueOf("ERR_INVALID_ORDER_STATUS")).isEqualTo(OrderExceptionEnum.ERR_INVALID_ORDER_STATUS);
        assertThat(OrderExceptionEnum.valueOf("ERR_CANNOT_CANCEL_ORDER")).isEqualTo(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER);
        assertThat(OrderExceptionEnum.valueOf("ERR_ORDER_EXPIRED")).isEqualTo(OrderExceptionEnum.ERR_ORDER_EXPIRED);
        assertThat(OrderExceptionEnum.valueOf("ERR_QUEUE_FULL")).isEqualTo(OrderExceptionEnum.ERR_QUEUE_FULL);
    }

    @Test
    @DisplayName("4xx, 5xx 에러는 올바른 HTTP 상태 코드 범위에 속한다")
    void httpStatusesAreInClientOrServerErrorRange() {
        for (OrderExceptionEnum errorEnum : OrderExceptionEnum.values()) {
            assertThat(errorEnum.getHttpStatus().is4xxClientError() || errorEnum.getHttpStatus().is5xxServerError())
                    .as("Error %s should be a 4xx or 5xx HTTP status", errorEnum.name())
                    .isTrue();
        }
    }
}