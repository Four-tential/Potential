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

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ReviewSummaryService {

    private final CourseRepository courseRepository;
    private final ChatClient reviewChatClient;
    private final Resource initPrompt;
    private final Resource updatePrompt;

    public ReviewSummaryService(
            CourseRepository courseRepository,
            @Qualifier("reviewChatClient") ChatClient reviewChatClient,
            @Value("classpath:ai/prompts/review-summary-init.st") Resource initPrompt,
            @Value("classpath:ai/prompts/review-summary-update.st") Resource updatePrompt
    ) {
        this.courseRepository = courseRepository;
        this.reviewChatClient = reviewChatClient;
        this.initPrompt = initPrompt;
        this.updatePrompt = updatePrompt;
    }

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
}