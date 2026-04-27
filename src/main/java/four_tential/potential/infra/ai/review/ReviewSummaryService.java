package four_tential.potential.infra.ai.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 후기 요약 AI 서비스(프로토 타입)
 *
 * <p><b>스터디 week2 PromptService 패턴 기반:</b>
 * {@code resources/prompts/review-summary.st} 파일을 로드하여
 * {@code {courseId}}, {@code {reviews}} 변수를 치환 후 LLM에 전달합니다.</p>
 *
 * <pre>
 * // 사용 예시
 * String summary = reviewSummaryService.summarize(courseId, reviewContents);
 * </pre>
 */
@Slf4j
@Service
public class ReviewSummaryService {

    private final ChatClient reviewChatClient;
    private final PromptTemplate reviewSummaryPromptTemplate;

    public ReviewSummaryService(
            @Qualifier("reviewChatClient") ChatClient reviewChatClient,
            PromptTemplate reviewSummaryPromptTemplate
    ) {
        this.reviewChatClient = reviewChatClient;
        this.reviewSummaryPromptTemplate = reviewSummaryPromptTemplate;
    }

    /**
     * 코스 후기 목록을 AI로 요약합니다.
     *
     * @param courseId       코스 ID
     * @param reviewContents 후기 텍스트 목록
     * @return 요약 결과 문자열
     */
    public String summarize(Long courseId, List<String> reviewContents) {
        log.info("[후기 요약 요청] courseId={}, 후기 수={}", courseId, reviewContents.size());

        // 스터디 week2 패턴: PromptTemplate으로 변수 치환
        Prompt prompt = reviewSummaryPromptTemplate.create(Map.of(
                "courseId", courseId,
                "reviews", formatReviews(reviewContents)
        ));

        return reviewChatClient.prompt(prompt)
                .call()
                .content();
    }

    private String formatReviews(List<String> reviewContents) {
        return IntStream.range(0, reviewContents.size())
                .mapToObj(i -> (i + 1) + ". " + reviewContents.get(i))
                .collect(Collectors.joining("\n"));
    }
}