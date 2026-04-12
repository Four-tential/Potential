package four_tential.potential.infra.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisConstants {
    public static final String REFRESH_TOKEN_PREFIX = "Refresh-Token:";
    public static final String BLACK_LIST_PREFIX = "BlackList:";
}
