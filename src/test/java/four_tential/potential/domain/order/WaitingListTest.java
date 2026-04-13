package four_tential.potential.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WaitingList")
class WaitingListTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("주어진 파라미터로 WaitingList를 생성한다")
        void createsWaitingListWithGivenParameters() {
            int waitNumber = 5;

            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, waitNumber);

            assertThat(waitingList.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(waitingList.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(waitingList.getWaitNumber()).isEqualTo(waitNumber);
        }

        @Test
        @DisplayName("초기 상태는 PENDING이다")
        void initialStatusIsPending() {
            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            assertThat(waitingList.getStatus()).isEqualTo(WaitingStatus.PENDING);
        }

        @Test
        @DisplayName("waitedAt은 현재 시각으로 설정된다")
        void waitedAtIsSetToNow() {
            LocalDateTime before = LocalDateTime.now();

            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            LocalDateTime after = LocalDateTime.now();
            assertThat(waitingList.getWaitedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("expiredAt은 waitedAt으로부터 WAITING_LIST_EXPIRATION_MINUTES분 후로 설정된다")
        void expiredAtIsSetToWaitedAtPlusExpirationMinutes() {
            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            long minutesDiff = ChronoUnit.MINUTES.between(waitingList.getWaitedAt(), waitingList.getExpiredAt());
            assertThat(minutesDiff).isEqualTo(WaitingList.WAITING_LIST_EXPIRATION_MINUTES);
        }

        @Test
        @DisplayName("expiredAt은 waitedAt으로부터 정확히 30분 후이다")
        void expiredAtIsExactlyThirtyMinutesAfterWaitedAt() {
            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            assertThat(waitingList.getExpiredAt()).isEqualTo(waitingList.getWaitedAt().plusMinutes(30));
        }

        @Test
        @DisplayName("calledAt은 초기에 null이다")
        void calledAtIsInitiallyNull() {
            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            assertThat(waitingList.getCalledAt()).isNull();
        }

        @Test
        @DisplayName("id는 생성 직후 null이다 (UUID는 영속화 시 생성됨)")
        void idIsNullBeforePersistence() {
            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            assertThat(waitingList.getId()).isNull();
        }

        @Test
        @DisplayName("waitNumber가 1인 첫 번째 대기자를 생성할 수 있다")
        void canRegisterFirstWaiter() {
            WaitingList waitingList = WaitingList.register(MEMBER_ID, COURSE_ID, 1);

            assertThat(waitingList.getWaitNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("서로 다른 멤버와 코스 UUID로 고유한 대기 항목을 생성한다")
        void createDistinctEntriesForDifferentMembersAndCourses() {
            UUID memberId2 = UUID.randomUUID();
            UUID courseId2 = UUID.randomUUID();

            WaitingList first = WaitingList.register(MEMBER_ID, COURSE_ID, 1);
            WaitingList second = WaitingList.register(memberId2, courseId2, 2);

            assertThat(first.getMemberId()).isNotEqualTo(second.getMemberId());
            assertThat(first.getCourseId()).isNotEqualTo(second.getCourseId());
            assertThat(first.getWaitNumber()).isNotEqualTo(second.getWaitNumber());
        }

        @Test
        @DisplayName("WAITING_LIST_EXPIRATION_MINUTES 상수는 30이다")
        void waitingListExpirationMinutesConstantIsThirty() {
            assertThat(WaitingList.WAITING_LIST_EXPIRATION_MINUTES).isEqualTo(30);
        }
    }
}