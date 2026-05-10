package four_tential.potential.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.OAUTH_LINK_TICKET_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.OAUTH_LOGIN_TICKET_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.OAUTH_TICKET_TTL_SECONDS;

@Repository
@RequiredArgsConstructor
public class OAuthRedirectTicketRepository {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TICKET_BYTE_LENGTH = 32;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public String issueLogin(OAuthLoginTicketData data) {
        return store(OAUTH_LOGIN_TICKET_PREFIX, serialize(data));
    }

    public String issueLink(OAuthLinkTicketData data) {
        return store(OAUTH_LINK_TICKET_PREFIX, serialize(data));
    }

    public Optional<OAuthLoginTicketData> consumeLogin(String ticket) {
        return consume(OAUTH_LOGIN_TICKET_PREFIX + ticket, OAuthLoginTicketData.class);
    }

    public Optional<OAuthLinkTicketData> consumeLink(String ticket) {
        return consume(OAUTH_LINK_TICKET_PREFIX + ticket, OAuthLinkTicketData.class);
    }

    private String store(String prefix, String json) {
        String ticket = generateTicket();
        redisTemplate.opsForValue().set(prefix + ticket, json, OAUTH_TICKET_TTL_SECONDS, TimeUnit.SECONDS);
        return ticket;
    }

    private <T> Optional<T> consume(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().getAndDelete(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JacksonException e) {
            throw new IllegalStateException("OAuth 리다이렉트 티켓 역직렬화 실패", e);
        }
    }

    private String serialize(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            throw new IllegalStateException("OAuth 리다이렉트 티켓 직렬화 실패", e);
        }
    }

    private String generateTicket() {
        byte[] bytes = new byte[TICKET_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
