package four_tential.potential.infra.batch.review;

import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.infra.ai.review.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReviewSummaryBatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReviewRepository reviewRepository;
    private final ReviewSummaryService reviewSummaryService;

    // 병렬 처리 스레드 수 — LLM API Rate Limit 고려
    private static final int THREAD_COUNT = 5;

    @Bean
    public Job reviewSummaryBatchJob() {
        return new JobBuilder("reviewSummaryBatchJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(reviewSummaryBatchStep())
                .build();
    }

    /**
     * Tasklet 방식으로 전체 후기 재요약 실행
     * - chunk 방식 제거: 저장이 Processor 내부(@Transactional)에서 이뤄지므로 chunk 이점 없음
     * - ForkJoinPool로 병렬 처리: LLM API Rate Limit 고려해 스레드 수 제한
     */
    @Bean
    public Step reviewSummaryBatchStep() {
        return new StepBuilder("reviewSummaryBatchStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    List<UUID> courseIds = reviewRepository.findDistinctCourseIds();
                    log.info("[배치 재요약] 대상 코스 수: {}건", courseIds.size());

                    ForkJoinPool pool = new ForkJoinPool(THREAD_COUNT);
                    try (pool) {
                        pool.submit(() ->
                                courseIds.parallelStream().forEach(courseId -> {
                                    List<String> contents = reviewRepository.findAllContentByCourseId(courseId);
                                    if (contents.isEmpty()) {
                                        log.info("[배치 재요약] 후기 없음, 스킵. courseId={}", courseId);
                                        return;
                                    }
                                    log.info("[배치 재요약] 요약 시작. courseId={}, 후기 수={}건", courseId, contents.size());
                                    try {
                                        reviewSummaryService.batchSummarize(courseId, contents);
                                        log.info("[배치 재요약] 완료. courseId={}", courseId);
                                    } catch (Exception e) {
                                        log.error("[배치 재요약] 실패. courseId={}", courseId, e);
                                    }
                                })
                        ).get();
                    }

                    log.info("[배치 재요약] 전체 완료. 처리 코스 수={}건", courseIds.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}