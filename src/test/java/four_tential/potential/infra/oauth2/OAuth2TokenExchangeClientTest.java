package four_tential.potential.infra.oauth2;

import four_tential.potential.common.exception.ServiceErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OAuth2TokenExchangeClientTest {

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @InjectMocks
    private OAuth2TokenExchangeClient client;

    @Test
    @DisplayName("미지원 provider - registration 이 null 이면 ERR_SOCIAL_PROVIDER_NOT_SUPPORTED")
    void exchangeAndFetch_unsupportedProvider() {
        given(clientRegistrationRepository.findByRegistrationId("unknown")).willReturn(null);

        assertThatThrownBy(() -> client.exchangeAndFetch("unknown", "code", "https://app/cb"))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("지원하지 않는 소셜 로그인입니다");
    }
}
