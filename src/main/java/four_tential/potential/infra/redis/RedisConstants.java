package four_tential.potential.infra.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisConstants {
    public static final String REFRESH_TOKEN_PREFIX = "Refresh-Token:";
    public static final String BLACK_LIST_PREFIX = "BlackList:";

    // Order
    public static final String COURSE_CAPACITY_PREFIX = "Course:Capacity:";
    public static final String WAITING_LIST_PREFIX = "WaitingList:";
    public static final String USER_COURSE_OCCUPANCY_PREFIX = "User:Occupancy:";
    public static final String WAITING_ORDER_COUNT_PREFIX = "WaitingCount:";

    // QR 출석
    public static final String QR_ATTENDANCE_PREFIX = "qr:attendance:"; // courseId 기준 중복 생성 방지
    public static final String QR_TOKEN_PREFIX = "qr:token:"; // token 기준 역조회
    public static final long   QR_TTL_SECONDS = 600L;

    // SSE 관련
    public static final String SSE_ATTENDANCE_PREFIX = "sse:attendance:";

    // Payment
    public static final String PAYMENT_ORDER_LOCK_PREFIX = "payment:order:";
    public static final String PAYMENT_COURSE_LOCK_PREFIX = "payment:course:";
    public static final String PAYMENT_PG_LOCK_PREFIX = "payment:pg:";
}
