package four_tential.potential.common.security;

import four_tential.potential.infra.oauth2.CustomOAuth2UserService;
import four_tential.potential.infra.oauth2.OAuth2LoginFailureHandler;
import four_tential.potential.infra.oauth2.OAuth2LoginSuccessHandler;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@TestConfiguration
public class WebMvcSecurityTestSupport {

    @Bean
    public CustomOAuth2UserService customOAuth2UserService() {
        return Mockito.mock(CustomOAuth2UserService.class);
    }

    @Bean
    public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler() {
        return Mockito.mock(OAuth2LoginSuccessHandler.class);
    }

    @Bean
    public OAuth2LoginFailureHandler oAuth2LoginFailureHandler() {
        return Mockito.mock(OAuth2LoginFailureHandler.class);
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration dummy = ClientRegistration.withRegistrationId("test-dummy")
                .clientId("dummy")
                .clientSecret("dummy")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/{registrationId}")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .build();
        return new InMemoryClientRegistrationRepository(dummy);
    }
}
