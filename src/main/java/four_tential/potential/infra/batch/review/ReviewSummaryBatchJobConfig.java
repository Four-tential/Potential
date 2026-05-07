package four_tential.potential.infra.batch.review;

import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.infra.ai.review.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReviewSummaryBatchJobConfig {

    private final JobRepository jobRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewSummaryService reviewSummaryService;

    @Bean
    public Job reviewSummaryBatchJob() {
        return new JobBuilder("reviewSummaryBatchJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(reviewSummaryBatchStep())
                .build();
    }

    @Bean
    public Step reviewSummaryBatchStep() {
        return new StepBuilder("reviewSummaryBatchStep", jobRepository)
                .<UUID, UUID>chunk(10)
                .reader(reviewSummaryCourseReader())
                .processor(reviewSummaryProcessor())
                .writer(reviewSummaryWriter())
                .build();
    }

    /**
     * Reader: 후기가 존재하는 코스 ID 목록을 읽어온다
     */
    @Bean
    @StepScope
    public ListItemReader<UUID> reviewSummaryCourseReader() {
        List<UUID> courseIds = reviewRepository.findDistinctCourseIds();
        log.info("[배치 재요약] 대상 코스 수: {}건", courseIds.size());
        return new ListItemReader<>(courseIds);
    }

    /**
     * Processor: 코스의 전체 후기 content를 LLM에 전달해 요약을 갱신한다
     * 전체 후기 원문을 사용하므로 누적 갱신 방식의 왜곡 문제를 해결한다
     */
    @Bean
    @StepScope
    public ItemProcessor<UUID, UUID> reviewSummaryProcessor() {
        return courseId -> {
            List<String> contents = reviewRepository.findAllContentByCourseId(courseId);

            if (contents.isEmpty()) {
                log.info("[배치 재요약] 후기 없음, 스킵. courseId={}", courseId);
                return null;
            }

            log.info("[배치 재요약] 요약 시작. courseId={}, 후기 수={}건", courseId, contents.size());

            try {
                reviewSummaryService.batchSummarize(courseId, contents);
                log.info("[배치 재요약] 완료. courseId={}", courseId);
                return courseId;
            } catch (Exception e) {
                log.error("[배치 재요약] 실패. courseId={}", courseId, e);
                return null;
            }
        };
    }

    /**
     * Writer: 처리 완료 로그만 기록 (실제 저장은 Processor의 batchSummarize 내부에서 처리)
     */
    @Bean
    public ItemWriter<UUID> reviewSummaryWriter() {
        return chunk -> log.info("[배치 재요약] Writer 완료. 처리 코스 수={}건", chunk.size());
    }
}