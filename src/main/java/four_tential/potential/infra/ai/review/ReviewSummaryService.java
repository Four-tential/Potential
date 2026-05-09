package four_tential.potential.infra.ai.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.CourseExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.infra.ai.advisor.AiLoggingAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Service
public class ReviewSummaryService {

    private final CourseRepository courseRepository;
    private final ChatClient reviewChatClient;
    private final Resource initPrompt;
    private final Resource updatePrompt;
    private final Resource chunkPrompt;
    private final Resource reducePrompt;

    // 청크당 후기 수 (토큰 제한 고려)
    private static final int CHUNK_SIZE = 100;

    public ReviewSummaryService(
            CourseRepository courseRepository,
            @Qualifier("reviewChatClient") ChatClient reviewChatClient,
            @Value("classpath:ai/prompts/review-summary-init.st") Resource initPrompt,
            @Value("classpath:ai/prompts/review-summary-update.st") Resource updatePrompt,
            @Value("classpath:ai/prompts/review-summary-chunk.st") Resource chunkPrompt,
            @Value("classpath:ai/prompts/review-summary-reduce.st") Resource reducePrompt
    ) {
        this.courseRepository = courseRepository;
        this.reviewChatClient = reviewChatClient;
        this.initPrompt = initPrompt;
        this.updatePrompt = updatePrompt;
        this.chunkPrompt = chunkPrompt;
        this.reducePrompt = reducePrompt;
    }

    @Async("reviewSummaryExecutor")
    @Transactional
    public void updateSummary(UUID courseId, String newReviewContent) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        String existingSummary = course.getSummary();
        log.info("[후기 요약 갱신] courseId={}, 기존요약존재={}", courseId, existingSummary != null);

        PromptTemplate template = existingSummary == null
                ? new PromptTemplate(initPrompt)
                : new PromptTemplate(updatePrompt);

        Map<String, Object> variables = existingSummary == null
                ? Map.of("newReview", newReviewContent)
                : Map.of("existingSummary", existingSummary, "newReview", newReviewContent);

        String updatedSummary = reviewChatClient.prompt(template.create(variables))
                .advisors(advisor -> advisor.param(AiLoggingAdvisor.CONTEXT_FEATURE, AiLoggingAdvisor.FEATURE_REVIEW_SUMMARY))
                .call()
                .content();

        course.updateSummary(updatedSummary);
    }

    /**
     * 배치 재요약 — Map-Reduce 방식
     * 전체 후기를 CHUNK_SIZE개씩 나눠 중간 요약(Map) 후,
     * 중간 요약들을 합쳐 최종 요약(Reduce)을 생성한다.
     * 후기 수가 많아도 토큰 제한 없이 처리 가능하다.
     */
    @Transactional
    public void batchSummarize(UUID courseId, List<String> allContents) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        log.info("[배치 재요약] Map-Reduce 시작. courseId={}, 전체 후기={}건, 청크 크기={}",
                courseId, allContents.size(), CHUNK_SIZE);

        //  Map: CHUNK_SIZE개씩 중간 요약 생성
        List<String> chunkSummaries = new ArrayList<>();
        for (int i = 0; i < allContents.size(); i += CHUNK_SIZE) {
            List<String> chunk = allContents.subList(i, Math.min(i + CHUNK_SIZE, allContents.size()));

            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < chunk.size(); j++) {
                sb.append(i + j + 1).append(". ").append(chunk.get(j)).append("\n");
            }

            String chunkSummary = reviewChatClient
                    .prompt(new PromptTemplate(chunkPrompt).create(Map.of("reviews", sb.toString())))
                    .advisors(advisor -> advisor.param(AiLoggingAdvisor.CONTEXT_FEATURE, AiLoggingAdvisor.FEATURE_REVIEW_SUMMARY))
                    .call()
                    .content();

            chunkSummaries.add(chunkSummary);
            log.info("[배치 재요약] 청크 요약 완료. courseId={}, 청크={}/{}",
                    courseId, (i / CHUNK_SIZE) + 1, (int) Math.ceil((double) allContents.size() / CHUNK_SIZE));
        }

        // Reduce: 중간 요약들을 합쳐 최종 요약 생성
        String finalSummary;
        if (chunkSummaries.size() == 1) {
            // 청크가 1개면 Reduce 불필요
            finalSummary = chunkSummaries.get(0);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < chunkSummaries.size(); i++) {
                sb.append(i + 1).append(". ").append(chunkSummaries.get(i)).append("\n");
            }

            finalSummary = reviewChatClient
                    .prompt(new PromptTemplate(reducePrompt).create(Map.of("summaries", sb.toString())))
                    .advisors(advisor -> advisor.param(AiLoggingAdvisor.CONTEXT_FEATURE, AiLoggingAdvisor.FEATURE_REVIEW_SUMMARY))
                    .call()
                    .content();
        }

        log.info("[배치 재요약] 최종 요약 저장 완료. courseId={}, 청크 수={}",
                courseId, chunkSummaries.size());
        course.updateSummary(finalSummary);
    }
}