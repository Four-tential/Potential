package four_tential.potential.infra.init;

import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class TestMemberInitializer implements ApplicationRunner {

    private static final String TEST_EMAIL = "nananan1213@gmail.com";
    private static final String TEST_NAME = "테스트 사용자";
    private static final String TEST_PHONE = "010-0000-0000";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.test-account.password}")
    private String testPassword;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (memberRepository.existsByEmail(TEST_EMAIL)) {
            log.info("테스트 계정 이미 존재 - {} (skip)", TEST_EMAIL);
            return;
        }

        Member member = Member.register(
                TEST_EMAIL,
                passwordEncoder.encode(testPassword),
                TEST_NAME,
                TEST_PHONE
        );
        memberRepository.save(member);

        log.info("=========================================================");
        log.info(" 테스트 계정 생성 완료");
        log.info("   email   : {}", TEST_EMAIL);
        log.info("   phone   : {}", TEST_PHONE);
        log.info("=========================================================");
    }
}
