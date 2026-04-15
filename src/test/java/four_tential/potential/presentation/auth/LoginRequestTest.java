package four_tential.potential.presentation.auth;

import four_tential.potential.presentation.auth.fixture.LoginRequestFixture;
import four_tential.potential.presentation.auth.model.request.LoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청 - 검증 통과")
    void validRequest() {
        LoginRequest request = LoginRequestFixture.defaultRequest();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일 공백 - 검증 실패")
    void blankEmail() {
        LoginRequest request = LoginRequestFixture.withEmail("");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("이메일 형식 불일치 - 검증 실패")
    void invalidEmailFormat() {
        LoginRequest request = LoginRequestFixture.withEmail("not-an-email");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("email") &&
                v.getMessage().equals("이메일 형식이 올바르지 않습니다")
        );
    }

    @Test
    @DisplayName("비밀번호 공백 - 검증 실패")
    void blankPassword() {
        LoginRequest request = LoginRequestFixture.withPassword("");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
