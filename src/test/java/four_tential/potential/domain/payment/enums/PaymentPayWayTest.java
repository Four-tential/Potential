package four_tential.potential.domain.payment.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentPayWayTest {

    @Test
    @DisplayName("from 호출 시 card 는 CARD 로 변환된다")
    void from_card() {
        assertThat(PaymentPayWay.from("card")).isEqualTo(PaymentPayWay.CARD);
    }

    @Test
    @DisplayName("from 호출 시 easyPay 는 EASY_PAY 로 변환된다")
    void from_easyPay() {
        assertThat(PaymentPayWay.from("easyPay")).isEqualTo(PaymentPayWay.EASY_PAY);
    }

    @Test
    @DisplayName("from 호출 시 알 수 없는 값은 예외를 던진다")
    void from_unknown_throws() {
        assertThatThrownBy(() -> PaymentPayWay.from("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}