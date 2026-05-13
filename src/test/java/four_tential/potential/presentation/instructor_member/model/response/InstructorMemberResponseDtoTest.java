package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.member.instructor_member.MyInstructorApplicationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InstructorMemberResponseDtoTest {

    @Test
    @DisplayName("ApplyInstructorResponse.register는 신청 정보를 올바르게 매핑한다")
    void applyInstructorResponse_register() {
        UUID memberId = UUID.randomUUID();
        InstructorMember im = InstructorMember.register(memberId, "YOGA", "경력 10년", "https://cdn.example.com/cert.jpg");

        ApplyInstructorResponse response = ApplyInstructorResponse.register(im);

        assertThat(response.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.categoryCode()).isEqualTo("YOGA");
    }

    @Test
    @DisplayName("InstructorActionResponse.register는 승인된 신청 정보를 올바르게 매핑한다")
    void instructorActionResponse_register_approved() {
        UUID memberId = UUID.randomUUID();
        InstructorMember im = InstructorMember.register(memberId, "PILATES", "필라테스 전문", "https://cdn.example.com/cert.jpg");
        im.approve();

        InstructorActionResponse response = InstructorActionResponse.register(im);

        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.status()).isEqualTo(InstructorMemberStatus.APPROVED);
        assertThat(response.respondedAt()).isNotNull();
    }

    @Test
    @DisplayName("MyInstructorApplicationResponse.register는 쿼리 결과를 올바르게 매핑한다")
    void myInstructorApplicationResponse_register() {
        LocalDateTime now = LocalDateTime.now();
        MyInstructorApplicationResult result = new MyInstructorApplicationResult(
                "YOGA", "요가", "10년 경력", "https://cdn.example.com/cert.jpg",
                InstructorMemberStatus.PENDING, null, now, null
        );

        MyInstructorApplicationResponse response = MyInstructorApplicationResponse.register(result);

        assertThat(response.categoryCode()).isEqualTo("YOGA");
        assertThat(response.categoryName()).isEqualTo("요가");
        assertThat(response.content()).isEqualTo("10년 경력");
        assertThat(response.status()).isEqualTo(InstructorMemberStatus.PENDING);
        assertThat(response.rejectReason()).isNull();
        assertThat(response.appliedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("MyInstructorApplicationResponse - 거절된 신청")
    void myInstructorApplicationResponse_rejected() {
        LocalDateTime applied = LocalDateTime.of(2025, 6, 1, 10, 0);
        LocalDateTime responded = LocalDateTime.of(2025, 6, 3, 14, 0);
        MyInstructorApplicationResult result = new MyInstructorApplicationResult(
                "YOGA", "요가", "경력 부족", "https://cdn.example.com/cert.jpg",
                InstructorMemberStatus.REJECTED, "포트폴리오 정보가 부족합니다", applied, responded
        );

        MyInstructorApplicationResponse response = MyInstructorApplicationResponse.register(result);

        assertThat(response.status()).isEqualTo(InstructorMemberStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("포트폴리오 정보가 부족합니다");
        assertThat(response.respondedAt()).isEqualTo(responded);
    }
}
