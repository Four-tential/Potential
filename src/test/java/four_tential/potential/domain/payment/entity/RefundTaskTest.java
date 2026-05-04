package four_tential.potential.domain.payment.entity;

import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundTaskTest {

    @Test
    @DisplayName("pending 팩토리는 PENDING 상태의 refund_task를 만든다")
    void pending_creates_pending_refund_task() {
        UUID courseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        RefundTask task = RefundTask.pending(courseId, orderId, paymentId, memberId);

        assertThat(task.getCourseId()).isEqualTo(courseId);
        assertThat(task.getOrderId()).isEqualTo(orderId);
        assertThat(task.getPaymentId()).isEqualTo(paymentId);
        assertThat(task.getMemberId()).isEqualTo(memberId);
        assertThat(task.getStatus()).isEqualTo(RefundTaskStatus.PENDING);
    }

    @Test
    @DisplayName("markDone은 상태를 DONE으로 바꾸고 실패 사유를 비운다")
    void markDone_sets_done_and_clears_reason() {
        RefundTask task = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        task.markFailed("failure");

        task.markDone();

        assertThat(task.getStatus()).isEqualTo(RefundTaskStatus.DONE);
        assertThat(task.getFailReason()).isNull();
    }

    @Test
    @DisplayName("markFailed는 상태를 FAILED로 바꾸고 실패 사유를 저장한다")
    void markFailed_sets_failed_and_reason() {
        RefundTask task = RefundTask.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        task.markFailed("failure");

        assertThat(task.getStatus()).isEqualTo(RefundTaskStatus.FAILED);
        assertThat(task.getFailReason()).isEqualTo("failure");
    }
}
