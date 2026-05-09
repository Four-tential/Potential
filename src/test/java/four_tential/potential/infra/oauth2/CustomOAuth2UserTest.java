package four_tential.potential.infra.oauth2;

import four_tential.potential.domain.member.member.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOAuth2UserTest {

    @Test
    @DisplayName("getName - memberId 의 toString")
    void getName_returnsMemberIdString() {
        UUID memberId = UUID.randomUUID();
        CustomOAuth2User user = new CustomOAuth2User(
                memberId, "user@example.com", MemberRole.ROLE_STUDENT, true, false, Map.of()
        );

        assertThat(user.getName()).isEqualTo(memberId.toString());
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getMemberId()).isEqualTo(memberId);
        assertThat(user.getRole()).isEqualTo(MemberRole.ROLE_STUDENT);
        assertThat(user.isHasOnboarding()).isTrue();
        assertThat(user.isRequiresPhoneSetup()).isFalse();
    }

    @Test
    @DisplayName("getAuthorities - role.name() 으로 SimpleGrantedAuthority 단일 반환")
    void getAuthorities_returnsRoleName() {
        CustomOAuth2User user = new CustomOAuth2User(
                UUID.randomUUID(), "user@example.com", MemberRole.ROLE_INSTRUCTOR, true, false, Map.of()
        );

        assertThat(user.getAuthorities()).hasSize(1);
        assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_INSTRUCTOR");
    }

    @Test
    @DisplayName("getAttributes - 생성자에 전달된 attributes 반환")
    void getAttributes_returnsConstructorAttributes() {
        Map<String, Object> attrs = Map.of("sub", "google-1", "email", "user@example.com");
        CustomOAuth2User user = new CustomOAuth2User(
                UUID.randomUUID(), "user@example.com", MemberRole.ROLE_STUDENT, false, true, attrs
        );

        assertThat(user.getAttributes()).isEqualTo(attrs);
    }
}
