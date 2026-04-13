package four_tential.potential.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

@DisplayName("Order")
class OrderTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("주어진 파라미터로 Order를 생성한다")
        void createsOrderWithGivenParameters() {
            BigInteger priceSnap = BigInteger.valueOf(10_000);
            int orderCount = 2;

            Order order = Order.register(MEMBER_ID, COURSE_ID, orderCount, priceSnap, "테스트 코스");

            assertThat(order.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(order.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(order.getOrderCount()).isEqualTo(orderCount);
            assertThat(order.getPriceSnap()).isEqualTo(priceSnap);
            assertThat(order.getTitleSnap()).isEqualTo("테스트 코스");
        }

        @Test
        @DisplayName("totalPriceSnap은 priceSnap * orderCount로 계산된다")
        void calculatesTotalPriceSnapCorrectly() {
            BigInteger priceSnap = BigInteger.valueOf(10_000);
            int orderCount = 3;

            Order order = Order.register(MEMBER_ID, COURSE_ID, orderCount, priceSnap, "코스");

            assertThat(order.getTotalPriceSnap()).isEqualTo(BigInteger.valueOf(30_000));
        }

        @Test
        @DisplayName("orderCount가 1일 때 totalPriceSnap은 priceSnap과 동일하다")
        void totalPriceEqualsUnitPriceWhenOrderCountIsOne() {
            BigInteger priceSnap = BigInteger.valueOf(50_000);

            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, priceSnap, "단일 코스");

            assertThat(order.getTotalPriceSnap()).isEqualTo(priceSnap);
        }

        @Test
        @DisplayName("초기 상태는 PENDING이다")
        void initialStatusIsPending() {
            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.TEN, "코스");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("orderAt은 현재 시각으로 설정된다")
        void orderAtIsSetToNow() {
            LocalDateTime before = LocalDateTime.now();

            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.TEN, "코스");

            LocalDateTime after = LocalDateTime.now();
            assertThat(order.getOrderAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("expireAt은 orderAt으로부터 ORDER_EXPIRATION_MINUTES분 후로 설정된다")
        void expireAtIsSetToOrderAtPlusExpirationMinutes() {
            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.TEN, "코스");

            long minutesDiff = ChronoUnit.MINUTES.between(order.getOrderAt(), order.getExpireAt());
            assertThat(minutesDiff).isEqualTo(Order.ORDER_EXPIRATION_MINUTES);
        }

        @Test
        @DisplayName("expireAt은 orderAt으로부터 정확히 10분 후이다")
        void expireAtIsExactlyTenMinutesAfterOrderAt() {
            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.TEN, "코스");

            assertThat(order.getExpireAt()).isEqualTo(order.getOrderAt().plusMinutes(10));
        }

        @Test
        @DisplayName("cancelledAt은 초기에 null이다")
        void cancelledAtIsInitiallyNull() {
            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.TEN, "코스");

            assertThat(order.getCancelledAt()).isNull();
        }

        @Test
        @DisplayName("id는 생성 직후 null이다 (UUID는 영속화 시 생성됨)")
        void idIsNullBeforePersistence() {
            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.TEN, "코스");

            assertThat(order.getId()).isNull();
        }

        @Test
        @DisplayName("큰 가격과 여러 수량에서 totalPriceSnap 오버플로우 없이 계산된다")
        void handleLargePriceAndCountWithoutOverflow() {
            BigInteger priceSnap = BigInteger.valueOf(999_999_999L);
            int orderCount = 100;

            Order order = Order.register(MEMBER_ID, COURSE_ID, orderCount, priceSnap, "고가 코스");

            assertThat(order.getTotalPriceSnap()).isEqualTo(BigInteger.valueOf(99_999_999_900L));
        }

        @Test
        @DisplayName("ORDER_EXPIRATION_MINUTES 상수는 10이다")
        void orderExpirationMinutesConstantIsTen() {
            assertThat(Order.ORDER_EXPIRATION_MINUTES).isEqualTo(10);
        }
    }
}