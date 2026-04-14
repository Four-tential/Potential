package four_tential.potential.infra.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisConstants {
    public static final String REFRESH_TOKEN_PREFIX = "Refresh-Token:";
    public static final String BLACK_LIST_PREFIX = "BlackList:";

    // Order
    public static final String COURSE_STOCK_PREFIX = "Course:Stock:";
    public static final String WAITING_LIST_PREFIX = "WaitingList:";
    public static final String USER_COURSE_OCCUPANCY_PREFIX = "User:Occupancy:";
    public static final String ORDER_LOCK_PREFIX = "lock:order:";
}
