package four_tential.potential.infra.qr;

import four_tential.potential.common.exception.ServiceErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

    private QrCodeGenerator qrCodeGenerator;

    @BeforeEach
    void setUp() {
        qrCodeGenerator = new QrCodeGenerator();
    }

    @Nested
    @DisplayName("generate() - QR 이미지 생성")
    class GenerateTest {

        @Test
        @DisplayName("유효한 토큰으로 QR 이미지를 생성하면 byte[] 를 반환한다")
        void generate_success() {
            // given
            String token = "test-qr-token-uuid-1234";

            // when
            byte[] result = qrCodeGenerator.generate(token);

            // then
            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("생성된 이미지는 PNG 시그니처로 시작한다")
        void generate_returnsPngBytes() {
            // given
            String token = "test-token";

            // when
            byte[] result = qrCodeGenerator.generate(token);

            // then
            // PNG 파일은 항상 0x89 0x50 0x4E 0x47 로 시작
            assertThat(result[0]).isEqualTo((byte) 0x89);
            assertThat(result[1]).isEqualTo((byte) 0x50);
            assertThat(result[2]).isEqualTo((byte) 0x4E);
            assertThat(result[3]).isEqualTo((byte) 0x47);
        }

        @Test
        @DisplayName("서로 다른 토큰으로 생성한 QR 이미지는 서로 다르다")
        void generate_differentTokensProduceDifferentImages() {
            // given
            String tokenA = "token-aaa";
            String tokenB = "token-bbb";

            // when
            byte[] resultA = qrCodeGenerator.generate(tokenA);
            byte[] resultB = qrCodeGenerator.generate(tokenB);

            // then
            assertThat(resultA).isNotEqualTo(resultB);
        }

        @Test
        @DisplayName("같은 토큰으로 생성한 QR 이미지는 동일하다")
        void generate_sameTokenProducesSameImage() {
            // given
            String token = "same-token-1234";

            // when
            byte[] resultA = qrCodeGenerator.generate(token);
            byte[] resultB = qrCodeGenerator.generate(token);

            // then
            assertThat(resultA).isEqualTo(resultB);
        }

        @Test
        @DisplayName("UUID 형식의 토큰으로 QR 이미지를 생성한다")
        void generate_withUuidToken() {
            // given
            String uuidToken = "550e8400-e29b-41d4-a716-446655440000";

            // when
            byte[] result = qrCodeGenerator.generate(uuidToken);

            // then
            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(0);
        }
    }
}