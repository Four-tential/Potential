package four_tential.potential.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    @DisplayName("이미지 없이 회원을 생성하면 필수 필드가 설정되고 profileImageUrl은 null")
    void registerWithoutProfileImage() {
        Member member = MemberFixture.defaultMember();

        assertThat(member.getEmail()).isEqualTo(MemberFixture.DEFAULT_EMAIL);
        assertThat(member.getPassword()).isEqualTo(MemberFixture.DEFAULT_PASSWORD);
        assertThat(member.getPhone()).isEqualTo(MemberFixture.DEFAULT_PHONE);
        assertThat(member.getName()).isEqualTo(MemberFixture.DEFAULT_NAME);
        assertThat(member.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("이미지와 함께 회원을 생성하면 profileImageUrl이 설정")
    void registerWithProfileImage() {
        Member member = MemberFixture.memberWithProfileImage();

        assertThat(member.getEmail()).isEqualTo(MemberFixture.DEFAULT_EMAIL);
        assertThat(member.getPassword()).isEqualTo(MemberFixture.DEFAULT_PASSWORD);
        assertThat(member.getPhone()).isEqualTo(MemberFixture.DEFAULT_PHONE);
        assertThat(member.getName()).isEqualTo(MemberFixture.DEFAULT_NAME);
        assertThat(member.getProfileImageUrl()).isEqualTo(MemberFixture.DEFAULT_PROFILE_IMAGE_URL);
    }

    @Test
    @DisplayName("생성된 회원은 id가 없고 role이 null")
    void registerInitialState() {
        Member member = MemberFixture.defaultMember();

        assertThat(member.getId()).isNull();
        assertThat(member.getRole()).isNull();
        assertThat(member.getWithdrawalAt()).isNull();
    }
}
