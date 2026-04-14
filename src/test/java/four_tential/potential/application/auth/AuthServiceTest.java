package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.member.member.MemberRole;
import four_tential.potential.domain.member.member.MemberStatus;
import four_tential.potential.presentation.auth.fixture.SignUpRequestFixture;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import four_tential.potential.presentation.auth.model.response.SignUpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공 - 응답 값 및 저장 검증")
    void signUp() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();
        given(memberRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");

        SignUpResponse response = authService.signUp(request);

        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.role()).isEqualTo(MemberRole.ROLE_STUDENT.name());
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE.name());
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("이메일 중복 - ServiceErrorException 발생")
    void signUpDuplicateEmail() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();
        given(memberRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 사용 중인 이메일 입니다");
        verify(memberRepository, never()).save(any(Member.class));
    }
}
