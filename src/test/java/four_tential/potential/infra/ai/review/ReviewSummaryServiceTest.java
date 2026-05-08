package four_tential.potential.infra.ai.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.review.review.ReviewSummaryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewSummaryService")
class ReviewSummaryServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private ChatClient reviewChatClient;
    @Mock private Course course;

    // 실제 .st 파일 대신 인라인 템플릿 사용 — PromptTemplate 변수 검증 오류 방지
    private final Resource initPrompt   = new ByteArrayResource("[긍정] {reviews}".getBytes());
    private final Resource updatePrompt = new ByteArrayResource("[긍정] {existingSummary} {newReview}".getBytes());
    private final Resource chunkPrompt  = new ByteArrayResource("[긍정] {reviews}".getBytes());
    private final Resource reducePrompt = new ByteArrayResource("[긍정] {summaries}".getBytes());

    private ReviewSummaryService reviewSummaryService;

    private static final UUID   COURSE_ID       = UUID.randomUUID();
    private static final int    NEW_RATING       = 5;
    private static final String NEW_REVIEW       = "강사님이 친절하고 설명이 명확했습니다.";
    private static final String EXISTING_SUMMARY = "[긍정] 전반적으로 만족도가 높은 클래스입니다.\n#친절한강사";

    @BeforeEach
    void setUp() {
        reviewSummaryService = new ReviewSummaryService(
                courseRepository, reviewChatClient,
                initPrompt, updatePrompt, chunkPrompt, reducePrompt
        );
    }

    private void mockChatClientReturns(String content) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec     = mock(ChatClient.CallResponseSpec.class);
        when(reviewChatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(content);
    }

    private void mockChatClientAlwaysReturns(String content) {
        when(reviewChatClient.prompt(any(Prompt.class))).thenAnswer(inv -> {
            ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec resp      = mock(ChatClient.CallResponseSpec.class);
            when(spec.call()).thenReturn(resp);
            when(resp.content()).thenReturn(content);
            return spec;
        });
    }

    // ── updateSummary ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateSummary() - 기존 요약이 없을 때 (첫 번째 후기)")
    class WhenNoExistingSummary {

        @BeforeEach
        void setUp() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(course.getSummary()).thenReturn(null);
        }

        @Test
        @DisplayName("initPrompt 템플릿으로 LLM을 호출한다")
        void callsLlmWithInitPrompt() {
            mockChatClientReturns("[긍정] 첫 요약 결과\n#친절한강사");
            reviewSummaryService.updateSummary(COURSE_ID, NEW_RATING, NEW_REVIEW);
            verify(reviewChatClient).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("LLM 응답으로 course.updateSummary()를 호출한다")
        void updatesCourseSummary() {
            String llmResponse = "[긍정] 첫 요약 결과\n#친절한강사";
            mockChatClientReturns(llmResponse);
            reviewSummaryService.updateSummary(COURSE_ID, NEW_RATING, NEW_REVIEW);
            verify(course).updateSummary(llmResponse);
        }
    }

    @Nested
    @DisplayName("updateSummary() - 기존 요약이 있을 때 (누적 갱신)")
    class WhenExistingSummaryExists {

        @BeforeEach
        void setUp() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(course.getSummary()).thenReturn(EXISTING_SUMMARY);
        }

        @Test
        @DisplayName("updatePrompt 템플릿으로 LLM을 호출한다")
        void callsLlmWithUpdatePrompt() {
            mockChatClientReturns("[긍정] 갱신된 요약\n#친절한강사");
            reviewSummaryService.updateSummary(COURSE_ID, NEW_RATING, NEW_REVIEW);
            verify(reviewChatClient).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("LLM 응답으로 course.updateSummary()를 호출한다")
        void updatesCourseSummary() {
            String llmResponse = "[긍정] 갱신된 요약\n#친절한강사";
            mockChatClientReturns(llmResponse);
            reviewSummaryService.updateSummary(COURSE_ID, NEW_RATING, NEW_REVIEW);
            verify(course).updateSummary(llmResponse);
        }
    }

    @Nested
    @DisplayName("updateSummary() - 예외 상황")
    class UpdateSummaryException {

        @Test
        @DisplayName("코스가 존재하지 않으면 ServiceErrorException을 던진다")
        void throwsWhenCourseNotFound() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewSummaryService.updateSummary(COURSE_ID, NEW_RATING, NEW_REVIEW))
                    .isInstanceOf(ServiceErrorException.class);

            verify(reviewChatClient, never()).prompt(any(Prompt.class));
        }
    }

    // ── batchSummarize ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("batchSummarize() - 청크가 1개일 때 (후기 100건 이하)")
    class WhenSingleChunk {

        @BeforeEach
        void setUp() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        }

        @Test
        @DisplayName("LLM을 1번만 호출하고 Reduce 없이 저장한다")
        void callsLlmOnceWithoutReduce() {
            List<ReviewSummaryItem> items = List.of(
                    new ReviewSummaryItem(5, "좋아요"),
                    new ReviewSummaryItem(4, "만족해요"),
                    new ReviewSummaryItem(3, "보통이에요"),
                    new ReviewSummaryItem(2, "별로에요"),
                    new ReviewSummaryItem(1, "나빠요")
            );
            mockChatClientReturns("[긍정] 청크 요약 결과\n#친절한강사");

            reviewSummaryService.batchSummarize(COURSE_ID, items);

            verify(reviewChatClient, times(1)).prompt(any(Prompt.class));
            verify(course).updateSummary("[긍정] 청크 요약 결과\n#친절한강사");
        }

        @Test
        @DisplayName("정확히 100건이면 청크 1개 — Reduce 없이 LLM 1번 호출한다")
        void exactly100ReviewsNoReduce() {
            List<ReviewSummaryItem> items = Collections.nCopies(100, new ReviewSummaryItem(5, "후기 내용"));
            mockChatClientReturns("[긍정] 청크 요약");

            reviewSummaryService.batchSummarize(COURSE_ID, items);

            verify(reviewChatClient, times(1)).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ServiceErrorException을 던진다")
        void throwsWhenCourseNotFound() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewSummaryService.batchSummarize(
                    COURSE_ID, List.of(new ReviewSummaryItem(5, "후기1"))))
                    .isInstanceOf(ServiceErrorException.class);

            verify(reviewChatClient, never()).prompt(any(Prompt.class));
        }
    }

    @Nested
    @DisplayName("batchSummarize() - 청크가 여러 개일 때 (후기 100건 초과)")
    class WhenMultipleChunks {

        @BeforeEach
        void setUp() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        }

        @Test
        @DisplayName("101건이면 Map 2번 + Reduce 1번 = LLM 3번 호출한다")
        void oneOverChunkSizeCallsReduce() {
            List<ReviewSummaryItem> items = Collections.nCopies(101, new ReviewSummaryItem(5, "후기 내용"));
            mockChatClientAlwaysReturns("[긍정] 요약");

            reviewSummaryService.batchSummarize(COURSE_ID, items);

            verify(reviewChatClient, times(3)).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("250건이면 Map 3번 + Reduce 1번 = LLM 4번 호출한다")
        void twoFiftyReviewsCallsLlmFourTimes() {
            List<ReviewSummaryItem> items = Collections.nCopies(250, new ReviewSummaryItem(4, "후기 내용"));
            mockChatClientAlwaysReturns("[긍정] 요약");

            reviewSummaryService.batchSummarize(COURSE_ID, items);

            verify(reviewChatClient, times(4)).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("최종 요약으로 course.updateSummary()를 호출한다")
        void savesFinalSummary() {
            List<ReviewSummaryItem> items = Collections.nCopies(101, new ReviewSummaryItem(5, "후기 내용"));
            mockChatClientAlwaysReturns("[긍정] 최종 요약\n#친절한강사");

            reviewSummaryService.batchSummarize(COURSE_ID, items);

            verify(course).updateSummary("[긍정] 최종 요약\n#친절한강사");
        }
    }
}