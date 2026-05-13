package four_tential.potential.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingListTest {

    @Test
    @DisplayName("대기열을 성공적으로 생성합니다")
    void register() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        int waitNumber = 10;

        // when
        WaitingList waitingList = WaitingList.register(memberId, courseId, waitNumber);

        // then
        assertThat(waitingList).isNotNull();
        assertThat(waitingList.getMemberId()).isEqualTo(memberId);
        assertThat(waitingList.getCourseId()).isEqualTo(courseId);
        assertThat(waitingList.getWaitNumber()).isEqualTo(waitNumber);
        assertThat(waitingList.getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(waitingList.getWaitedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(waitingList.getExpiredAt()).isEqualTo(waitingList.getWaitedAt().plusMinutes(WaitingList.WAITING_LIST_EXPIRATION_MINUTES));
    }
}
