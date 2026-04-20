package four_tential.potential.application.order;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderConstants {
    // 한 강의당 최대 대기열 인원 제한 수
    public static final int MAX_WAITING_SIZE = 200;

    // 주문 생성 관련 응답 메시지
    public static final String MESSAGE_ORDER_SUCCESS = "재고 점유 성공. 10분 내 결제 요망";
    public static final String MESSAGE_WAITING_COMPLETED = "대기열 진입 완료";

    // 만료 시간 관련 (분 단위)
    public static final int PENDING_ORDER_EXPIRATION_MINUTES = 10;
    public static final int PROMOTION_EXPIRATION_MINUTES = 10;

    // Redis 저장 값 관련
    public static final String TOKEN_PROMOTED = "PROMOTED";
}
