package four_tential.potential.infra.ai.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewSummaryService")
class ReviewSummaryServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private ChatClient reviewChatClient;
    @Mock private Course course;

    private final Resource initPrompt   = new ClassPathResource("ai/prompts/review-summary-init.st");
    private final Resource updatePrompt = new ClassPathResource("ai/prompts/review-summary-update.st");
    private final Resource chunkPrompt  = new ClassPathResource("ai/prompts/review-summary-chunk.st");
    private final Resource reducePrompt = new ClassPathResource("ai/prompts/review-summary-reduce.st");

    private ReviewSummaryService reviewSummaryService;

    private static final UUID   COURSE_ID       = UUID.randomUUID();
    private static final String NEW_REVIEW       = "강사님이 친절하고 설명이 명확했습니다.";
    private static final String EXISTING_SUMMARY = "전반적으로 만족도가 높은 클래스입니다.";

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
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(content);
    }

    private void mockChatClientAlwaysReturns(String content) {
        when(reviewChatClient.prompt(any(Prompt.class))).thenAnswer(inv -> {
            ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec resp      = mock(ChatClient.CallResponseSpec.class);
            when(spec.advisors(any(Consumer.class))).thenReturn(spec);
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
            mockChatClientReturns("첫 요약 결과");
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);
            verify(reviewChatClient).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("LLM 응답으로 course.updateSummary()를 호출한다")
        void updatesCourseSummary() {
            mockChatClientReturns("첫 요약 결과");
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);
            verify(course).updateSummary("첫 요약 결과");
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
            mockChatClientReturns("갱신된 요약 결과");
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);
            verify(reviewChatClient).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("LLM 응답으로 course.updateSummary()를 호출한다")
        void updatesCourseSummary() {
            mockChatClientReturns("갱신된 요약");
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);
            verify(course).updateSummary("갱신된 요약");
        }
    }

    @Nested
    @DisplayName("updateSummary() - 예외 상황")
    class UpdateSummaryException {

        @Test
        @DisplayName("코스가 존재하지 않으면 ServiceErrorException을 던진다")
        void throwsWhenCourseNotFound() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW))
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
            List<String> contents = List.of("후기1", "후기2", "후기3", "후기4", "후기5");
            mockChatClientReturns("청크 요약 결과");

            reviewSummaryService.batchSummarize(COURSE_ID, contents);

            verify(reviewChatClient, times(1)).prompt(any(Prompt.class));
            verify(course).updateSummary("청크 요약 결과");
        }

        @Test
        @DisplayName("정확히 100건이면 청크 1개 — Reduce 없이 LLM 1번 호출한다")
        void exactly100ReviewsNoReduce() {
            List<String> contents = Collections.nCopies(100, "후기 내용");
            mockChatClientReturns("청크 요약");

            reviewSummaryService.batchSummarize(COURSE_ID, contents);

            verify(reviewChatClient, times(1)).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ServiceErrorException을 던진다")
        void throwsWhenCourseNotFound() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewSummaryService.batchSummarize(COURSE_ID, List.of("후기1")))
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
            List<String> contents = Collections.nCopies(101, "후기 내용");
            mockChatClientAlwaysReturns("요약");

            reviewSummaryService.batchSummarize(COURSE_ID, contents);

            verify(reviewChatClient, times(3)).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("250건이면 Map 3번 + Reduce 1번 = LLM 4번 호출한다")
        void twoFiftyReviewsCallsLlmFourTimes() {
            List<String> contents = Collections.nCopies(250, "후기 내용");
            mockChatClientAlwaysReturns("요약");

            reviewSummaryService.batchSummarize(COURSE_ID, contents);

            verify(reviewChatClient, times(4)).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("최종 요약으로 course.updateSummary()를 호출한다")
        void savesFinalSummary() {
            List<String> contents = Collections.nCopies(101, "후기 내용");
            mockChatClientAlwaysReturns("최종 요약");

            reviewSummaryService.batchSummarize(COURSE_ID, contents);

            verify(course).updateSummary("최종 요약");
        }
    }
}