package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCancelOutboxTest {

    @Test
    @DisplayName("pending 팩토리는 PENDING 상태의 outbox를 만든다")
    void pending_creates_pending_outbox() {
        UUID courseId = UUID.randomUUID();

        CourseCancelOutbox outbox = CourseCancelOutbox.pending(courseId);

        assertThat(outbox.getCourseId()).isEqualTo(courseId);
        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.PENDING);
        assertThat(outbox.getFailReason()).isNull();
    }

    @Test
    @DisplayName("markDone은 상태를 DONE으로 변경한다")
    void markDone_sets_done() {
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(UUID.randomUUID());

        outbox.markDone();

        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.DONE);
    }

    @Test
    @DisplayName("markFailed는 상태를 FAILED로 변경하고 실패 사유를 저장한다")
    void markFailed_sets_failed_and_reason() {
        CourseCancelOutbox outbox = CourseCancelOutbox.pending(UUID.randomUUID());

        outbox.markFailed("failure");

        assertThat(outbox.getStatus()).isEqualTo(CourseCancelOutboxStatus.FAILED);
        assertThat(outbox.getFailReason()).isEqualTo("failure");
    }
}
