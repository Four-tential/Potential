package four_tential.potential.infra.batch.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Profile("local")
@RestController
@RequestMapping("/ai/test")
public class BatchAdminController {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job reviewSummaryBatchJob;

    public BatchAdminController(
            JobOperator jobOperator,
            JobRepository jobRepository,
            @Qualifier("reviewSummaryBatchJob") Job reviewSummaryBatchJob
    ) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.reviewSummaryBatchJob = reviewSummaryBatchJob;
    }

    /**
     * 후기 요약 배치 수동 실행 (로컬 테스트용)
     * POST /v1/admin/batch/review-summary
     */
    @PostMapping("/review-summary")
    public ResponseEntity<String> runReviewSummaryBatch() {
        log.info("[배치 수동 실행] reviewSummaryBatchJob 시작");
        try {
            boolean running = !jobRepository.findRunningJobExecutions(reviewSummaryBatchJob.getName()).isEmpty();
            if (running) {
                return ResponseEntity.ok("이미 실행 중인 reviewSummaryBatchJob이 있습니다");
            }
            jobOperator.start(reviewSummaryBatchJob, new JobParameters());
            return ResponseEntity.ok("reviewSummaryBatchJob 실행 요청 완료");
        } catch (Exception e) {
            log.error("[배치 수동 실행] 실패", e);
            return ResponseEntity.internalServerError().body("실행 실패: " + e.getMessage());
        }
    }
}