package four_tential.potential.infra.ai.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.CourseExceptionEnum;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Resource batchPrompt;

    public ReviewSummaryService(
            CourseRepository courseRepository,
            @Qualifier("reviewChatClient") ChatClient reviewChatClient,
            @Value("classpath:ai/prompts/review-summary-init.st") Resource initPrompt,
            @Value("classpath:ai/prompts/review-summary-update.st") Resource updatePrompt,
            @Value("classpath:ai/prompts/review-summary-batch.st") Resource batchPrompt
    ) {
        this.courseRepository = courseRepository;
        this.reviewChatClient = reviewChatClient;
        this.initPrompt = initPrompt;
        this.updatePrompt = updatePrompt;
        this.batchPrompt = batchPrompt;
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
                .call()
                .content();

        course.updateSummary(updatedSummary);
    }

    /**
     * 배치 재요약 — 전체 후기 원문을 LLM에 전달해 요약을 처음부터 다시 생성한다.
     * 누적 갱신 방식의 왜곡 문제를 주기적으로 보정하기 위해 사용한다.
     * @Async 없이 배치 스레드에서 동기 실행
     */
    @Transactional
    public void batchSummarize(UUID courseId, List<String> allContents) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(CourseExceptionEnum.ERR_NOT_FOUND_COURSE));

        // 후기 목록을 번호 붙여 하나의 문자열로 결합
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allContents.size(); i++) {
            sb.append(i + 1).append(". ").append(allContents.get(i)).append("\n");
        }

        String updatedSummary = reviewChatClient
                .prompt(new PromptTemplate(batchPrompt).create(Map.of("reviews", sb.toString())))
                .call()
                .content();

        log.info("[배치 재요약] 저장 완료. courseId={}, 후기 수={}건", courseId, allContents.size());
        course.updateSummary(updatedSummary);
    }
}