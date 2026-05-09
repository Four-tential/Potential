package four_tential.potential.infra.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisConstants {
    public static final String REFRESH_TOKEN_PREFIX = "Refresh-Token:";
    public static final String BLACK_LIST_PREFIX = "BlackList:";

    public static final String SOCIAL_LINK_CHALLENGE_PREFIX = "social-link-challenge:";
    public static final String SOCIAL_LINK_ATTEMPTS_PREFIX = "social-link-attempts:";
    public static final long   SOCIAL_LINK_CHALLENGE_TTL_SECONDS = 300L;
    public static final int    SOCIAL_LINK_MAX_ATTEMPTS = 3;

    // OAuth2 1회용 리다이렉트 티켓 — accessToken/challengeToken을 URL에 노출하지 않기 위한 인디렉션
    public static final String OAUTH_LOGIN_TICKET_PREFIX = "oauth-login-ticket:";
    public static final String OAUTH_LINK_TICKET_PREFIX  = "oauth-link-ticket:";
    public static final long   OAUTH_TICKET_TTL_SECONDS  = 60L;

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

    // Review 캐시
    public static final String REVIEW_LIST_CACHE = "reviewList";  // 후기 목록 캐시

    // Order 캐시
    public static final String ORDER_DETAILS_CACHE = "orderDetails";
    public static final String ORDER_LIST_CACHE = "orderList";

    // Instructor Profile 캐시
    public static final String INSTRUCTOR_PROFILE_CACHE = "instructorProfile";

    // 팔로우 목록 캐시
    public static final String MY_FOLLOWS_CACHE = "myFollows";

    // 마이페이지 캐시
    public static final String MY_PAGE_CACHE = "myPage";

    // Attendance 캐시
    public static final String ATTENDANCE_LIST_CACHE = "attendanceList";  // 출석 현황 캐시

    // Course 캐시
    public static final String COURSE_DETAIL_CACHE = "courseDetail";

    // 결제 캐시
    public static final String PAYMENT_DETAIL_CACHE = "paymentDetail";
    public static final String PAYMENT_LIST_CACHE   = "paymentList";

    // 환불 캐시
    public static final String REFUND_DETAIL_CACHE  = "refundDetail";
    public static final String REFUND_LIST_CACHE    = "refundList";
}