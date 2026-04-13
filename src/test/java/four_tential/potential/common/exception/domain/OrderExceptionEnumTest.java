package four_tential.potential.common.exception.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExceptionEnumTest {

    @Test
    @DisplayName("주문 관련 예외 Enum이 올바른 메시지와 상태코드를 가집니다")
    void checkOrderExceptionEnum() {
        for (OrderExceptionEnum exceptionEnum : OrderExceptionEnum.values()) {
            assertThat(exceptionEnum.getHttpStatus()).isNotNull();
            assertThat(exceptionEnum.getMessage()).isNotBlank();
        }
    }

    @Test
    @DisplayName("특정 예외 케이스의 값을 검증합니다")
    void validateSpecificException() {
        assertThat(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(OrderExceptionEnum.ERR_NOT_FOUND_ORDER.getMessage()).isEqualTo("주문 정보를 찾을 수 없습니다");
    }
}
