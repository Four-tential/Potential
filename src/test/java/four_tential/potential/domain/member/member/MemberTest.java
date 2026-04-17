package four_tential.potential.domain.member.member;

import four_tential.potential.domain.member.fixture.MemberFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    @DisplayName("회원 생성 시 필수 필드가 설정되고 role은 STUDENT, status는 ACTIVE로 생성")
    void register() {
        Member member = MemberFixture.defaultMember();

        assertThat(member.getEmail()).isEqualTo(MemberFixture.DEFAULT_EMAIL);
        assertThat(member.getPassword()).isEqualTo(MemberFixture.DEFAULT_PASSWORD);
        assertThat(member.getPhone()).isEqualTo(MemberFixture.DEFAULT_PHONE);
        assertThat(member.getName()).isEqualTo(MemberFixture.DEFAULT_NAME);
        assertThat(member.getRole()).isEqualTo(MemberRole.ROLE_STUDENT);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("생성된 회원은 id와 withdrawalAt이 null")
    void registerInitialState() {
        Member member = MemberFixture.defaultMember();

        assertThat(member.getId()).isNull();
        assertThat(member.getWithdrawalAt()).isNull();
    }

    @Test
    @DisplayName("activate()를 호출하면 status가 ACTIVE")
    void activate() {
        Member member = MemberFixture.defaultMember();
        member.suspend();

        member.activate();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("suspend()를 호출하면 status가 SUSPENDED")
    void suspend() {
        Member member = MemberFixture.defaultMember();

        member.suspend();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    @DisplayName("withdraw()를 호출하면 status가 WITHDRAWAL이 되고 withdrawalAt이 설정")
    void withdraw() {
        Member member = MemberFixture.defaultMember();
        LocalDateTime before = LocalDateTime.now();

        member.withdraw();

        LocalDateTime after = LocalDateTime.now();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWAL);
        assertThat(member.getWithdrawalAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("changePassword() - 새 비밀번호로 변경되어 getPassword()가 새 값 반환")
    void changePassword_success() {
        Member member = MemberFixture.defaultMember();
        String newEncodedPassword = "newEncodedPassword!";

        member.changePassword(newEncodedPassword);

        assertThat(member.getPassword()).isEqualTo(newEncodedPassword);
    }

    @Test
    @DisplayName("changePassword() - 이전 비밀번호는 더 이상 반환되지 않음")
    void changePassword_oldPasswordNotRetained() {
        Member member = MemberFixture.defaultMember();
        String originalPassword = member.getPassword();

        member.changePassword("newEncodedPassword!");

        assertThat(member.getPassword()).isNotEqualTo(originalPassword);
    }

    @Test
    @DisplayName("changePassword() - 연속으로 두 번 호출하면 마지막 비밀번호로 최종 반영")
    void changePassword_calledTwice_lastValueRetained() {
        Member member = MemberFixture.defaultMember();

        member.changePassword("firstNewPassword!");
        member.changePassword("secondNewPassword!");

        assertThat(member.getPassword()).isEqualTo("secondNewPassword!");
    }
}
