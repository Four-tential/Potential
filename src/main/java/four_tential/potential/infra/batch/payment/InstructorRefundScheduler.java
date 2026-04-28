package four_tential.potential.infra.batch.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 강사 코스 취소 환불 Job 스케줄러
 *
 * [스케줄 주기 조정]
 * cron = "0 *\/5 * * * *" → 매 5분마다 실행
 * 더 자주 처리하고 싶으면 "0 * * * * *" (매 1분) 으로 변경
 * fixedDelay 방식 사용도 가능: @Scheduled(fixedDelay = 300_000) // 5분
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstructorRefundScheduler {

    // Todo: 스케줄러 작성

}
