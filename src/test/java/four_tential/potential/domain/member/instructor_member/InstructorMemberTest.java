package four_tential.potential.domain.member.instructor_member;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.fixture.InstructorMemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstructorMemberTest {

    @Test
    @DisplayName("apply() 성공 시 필수 필드가 설정되고 status는 PENDING")
    void apply() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();

        assertThat(instructorMember.getMemberId()).isEqualTo(InstructorMemberFixture.DEFAULT_MEMBER_ID);
        assertThat(instructorMember.getCategoryCode()).isEqualTo(InstructorMemberFixture.DEFAULT_CATEGORY_CODE);
        assertThat(instructorMember.getContent()).isEqualTo(InstructorMemberFixture.DEFAULT_CONTENT);
        assertThat(instructorMember.getImageUrl()).isEqualTo(InstructorMemberFixture.DEFAULT_IMAGE_URL);
        assertThat(instructorMember.getStatus()).isEqualTo(InstructorMemberStatus.PENDING);
    }

    @Test
    @DisplayName("생성된 강사 신청은 id, rejectReason, approvedAt, responsedAt이 null")
    void applyInitialState() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();

        assertThat(instructorMember.getId()).isNull();
        assertThat(instructorMember.getRejectReason()).isNull();
        assertThat(instructorMember.getApprovedAt()).isNull();
        assertThat(instructorMember.getResponsedAt()).isNull();
    }

    @Test
    @DisplayName("approve() 호출 시 status가 APPROVED, approvedAt과 responsedAt이 설정됨")
    void approve() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();
        LocalDateTime before = LocalDateTime.now();

        instructorMember.approve();

        LocalDateTime after = LocalDateTime.now();
        assertThat(instructorMember.getStatus()).isEqualTo(InstructorMemberStatus.APPROVED);
        assertThat(instructorMember.getApprovedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(instructorMember.getResponsedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("PENDING 이 아닌 상태에서 approve() 호출 시 ERR_INVALID_STATUS_TRANSITION_TO_APPROVE 예외 발생")
    void approve_notInPending() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();
        instructorMember.approve();

        assertThatThrownBy(instructorMember::approve)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("PENDING 상태의 신청 건만 승인 할 수 있습니다");
    }

    @Test
    @DisplayName("reject() 호출 시 status가 REJECTED, rejectReason과 responsedAt이 설정됨")
    void reject() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();
        LocalDateTime before = LocalDateTime.now();

        instructorMember.reject(InstructorMemberFixture.DEFAULT_REJECT_REASON);

        LocalDateTime after = LocalDateTime.now();
        assertThat(instructorMember.getStatus()).isEqualTo(InstructorMemberStatus.REJECTED);
        assertThat(instructorMember.getRejectReason()).isEqualTo(InstructorMemberFixture.DEFAULT_REJECT_REASON);
        assertThat(instructorMember.getResponsedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("reject() 호출 시 approvedAt은 null 유지")
    void reject_approvedAtRemainsNull() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();

        instructorMember.reject(InstructorMemberFixture.DEFAULT_REJECT_REASON);

        assertThat(instructorMember.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("PENDING 이 아닌 상태에서 reject() 호출 시 ERR_INVALID_STATUS_TRANSITION_TO_REJECT 예외 발생")
    void reject_notInPending() {
        InstructorMember instructorMember = InstructorMemberFixture.defaultInstructorMember();
        instructorMember.approve();

        assertThatThrownBy(() -> instructorMember.reject(InstructorMemberFixture.DEFAULT_REJECT_REASON))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("PENDING 상태의 신청 건만 거절 할 수 있습니다");
    }
}
