package four_tential.potential.infra.oauth2;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2TokenExchangeClient {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutRequestFactory())
            .build();

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    public OAuth2UserAttributes exchangeAndFetch(String registrationId, String code, String redirectUri) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
        if (registration == null) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROVIDER_NOT_SUPPORTED);
        }

        Map<String, Object> tokenResponse = exchangeCodeForToken(registration, code, redirectUri);
        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null) {
            log.warn("OAuth2 token 응답에 access_token 없음 - registration={}", registrationId);
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROVIDER_NOT_SUPPORTED);
        }

        Map<String, Object> userInfo = fetchUserInfo(registration, accessToken);
        return OAuth2UserAttributes.from(registrationId, userInfo);
    }

    private Map<String, Object> exchangeCodeForToken(ClientRegistration registration, String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", registration.getClientId());
        if (registration.getClientSecret() != null && !registration.getClientSecret().isBlank()) {
            form.add("client_secret", registration.getClientSecret());
        }

        return restClient.post()
                .uri(registration.getProviderDetails().getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> fetchUserInfo(ClientRegistration registration, String accessToken) {
        return restClient.get()
                .uri(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
