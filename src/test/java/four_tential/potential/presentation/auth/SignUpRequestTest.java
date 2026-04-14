package four_tential.potential.presentation.auth;

import four_tential.potential.presentation.auth.fixture.SignUpRequestFixture;
import four_tential.potential.presentation.auth.model.request.SignUpRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청 - 검증 통과")
    void validRequest() {
        SignUpRequest request = SignUpRequestFixture.defaultRequest();

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일 공백 - 검증 실패")
    void blankEmail() {
        SignUpRequest request = SignUpRequestFixture.withEmail("");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("이메일 형식 불일치 - 검증 실패")
    void invalidEmailFormat() {
        SignUpRequest request = SignUpRequestFixture.withEmail("not-an-email");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("email") &&
                v.getMessage().equals("이메일 형식이 아닙니다")
        );
    }

    @Test
    @DisplayName("비밀번호 공백 - 검증 실패")
    void blankPassword() {
        SignUpRequest request = SignUpRequestFixture.withPassword("");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("비밀번호 정책 불충족(12자 미만) - 검증 실패")
    void weakPassword() {
        SignUpRequest request = SignUpRequestFixture.withPassword("Pass1!");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("password") &&
                v.getMessage().contains("12자 이상")
        );
    }

    @Test
    @DisplayName("휴대전화 번호 공백 - 검증 실패")
    void blankPhone() {
        SignUpRequest request = SignUpRequestFixture.withPhone("");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }

    @Test
    @DisplayName("휴대전화 번호 형식 불일치(하이픈 없음) - 검증 실패")
    void invalidPhoneFormat() {
        SignUpRequest request = SignUpRequestFixture.withPhone("01012345678");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("phone") &&
                v.getMessage().contains("형식")
        );
    }
}
