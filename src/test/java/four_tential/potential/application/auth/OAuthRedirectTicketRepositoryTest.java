package four_tential.potential.application.auth;

import four_tential.potential.domain.member.social.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.OAUTH_LINK_TICKET_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.OAUTH_LOGIN_TICKET_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.OAUTH_TICKET_TTL_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthRedirectTicketRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;

    @InjectMocks
    private OAuthRedirectTicketRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new OAuthRedirectTicketRepository(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("issueLogin - 직렬화 후 prefix + ticket 키로 60초 TTL 저장")
    void issueLogin_storesWithTtl() {
        OAuthLoginTicketData data = new OAuthLoginTicketData("access-token", true, false);

        String ticket = repository.issueLogin(data);

        assertThat(ticket).isNotBlank();
        verify(valueOperations).set(
                startsWith(OAUTH_LOGIN_TICKET_PREFIX),
                anyString(),
                eq(OAUTH_TICKET_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("issueLink - link prefix 키로 저장")
    void issueLink_storesWithLinkPrefix() {
        OAuthLinkTicketData data = new OAuthLinkTicketData("challenge-token", "user@example.com", SocialProvider.KAKAO);

        String ticket = repository.issueLink(data);

        assertThat(ticket).isNotBlank();
        verify(valueOperations).set(
                startsWith(OAUTH_LINK_TICKET_PREFIX),
                anyString(),
                eq(OAUTH_TICKET_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("consumeLogin - 정상 JSON 이면 역직렬화하여 반환")
    void consumeLogin_returnsData() {
        OAuthLoginTicketData expected = new OAuthLoginTicketData("access-token", true, false);
        String json = objectMapper.writeValueAsString(expected);
        given(valueOperations.getAndDelete(OAUTH_LOGIN_TICKET_PREFIX + "ticket-1")).willReturn(json);

        Optional<OAuthLoginTicketData> result = repository.consumeLogin("ticket-1");

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("access-token");
        assertThat(result.get().hasOnboarding()).isTrue();
        assertThat(result.get().requiresPhoneSetup()).isFalse();
    }

    @Test
    @DisplayName("consumeLogin - 키 없으면 Optional.empty()")
    void consumeLogin_missingTicket() {
        given(valueOperations.getAndDelete(OAUTH_LOGIN_TICKET_PREFIX + "ticket-x")).willReturn(null);

        Optional<OAuthLoginTicketData> result = repository.consumeLogin("ticket-x");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("consumeLink - 정상 JSON 이면 역직렬화하여 반환")
    void consumeLink_returnsData() {
        OAuthLinkTicketData expected = new OAuthLinkTicketData("challenge-1", "user@example.com", SocialProvider.GOOGLE);
        String json = objectMapper.writeValueAsString(expected);
        given(valueOperations.getAndDelete(OAUTH_LINK_TICKET_PREFIX + "lt-1")).willReturn(json);

        Optional<OAuthLinkTicketData> result = repository.consumeLink("lt-1");

        assertThat(result).isPresent();
        assertThat(result.get().challengeToken()).isEqualTo("challenge-1");
        assertThat(result.get().email()).isEqualTo("user@example.com");
        assertThat(result.get().provider()).isEqualTo(SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("consumeLink - 잘못된 JSON 이면 IllegalStateException")
    void consumeLink_brokenJson() {
        given(valueOperations.getAndDelete(OAUTH_LINK_TICKET_PREFIX + "broken")).willReturn("{not-json");

        assertThatThrownBy(() -> repository.consumeLink("broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth 리다이렉트 티켓 역직렬화 실패");
    }
}
