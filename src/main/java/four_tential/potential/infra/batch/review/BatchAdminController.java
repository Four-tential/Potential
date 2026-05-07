package four_tential.potential.infra.batch.review;

import four_tential.potential.infra.batch.review.ReviewSummaryBatchJobScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Profile("local")  // 로컬 환경에서만 활성화
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/test")
public class BatchAdminController {

    private final ReviewSummaryBatchJobScheduler reviewSummaryBatchJobScheduler;

    /**
     * 후기 요약 배치 수동 실행 (로컬 테스트용)
     * POST /v1/admin/batch/review-summary
     */
    @PostMapping("/review-summary")
    public ResponseEntity<String> runReviewSummaryBatch() {
        log.info("[배치 수동 실행] reviewSummaryBatchJob 시작");
        reviewSummaryBatchJobScheduler.runReviewSummaryBatchJob();
        return ResponseEntity.ok("reviewSummaryBatchJob 실행 요청 완료");
    }
}