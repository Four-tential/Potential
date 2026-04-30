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

    // 실제 .st 파일을 사용 (클래스패스에 존재)
    private final Resource initPrompt = new ClassPathResource("ai/prompts/review-summary-init.st");
    private final Resource updatePrompt = new ClassPathResource("ai/prompts/review-summary-update.st");

    private ReviewSummaryService reviewSummaryService;

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final String NEW_REVIEW = "강사님이 친절하고 설명이 명확했습니다.";
    private static final String EXISTING_SUMMARY = "전반적으로 만족도가 높은 클래스입니다.";

    @BeforeEach
    void setUp() {
        reviewSummaryService = new ReviewSummaryService(
                courseRepository, reviewChatClient, initPrompt, updatePrompt
        );
    }

    // ChatClient 체이닝 Mock 세팅 헬퍼
    private void mockChatClientReturns(String content) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(reviewChatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(content);
    }

    @Nested
    @DisplayName("기존 요약이 없을 때 (첫 번째 후기)")
    class WhenNoExistingSummary {

        @BeforeEach
        void setUp() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(course.getSummary()).thenReturn(null);
        }

        @Test
        @DisplayName("initPrompt 템플릿으로 LLM을 호출한다")
        void callsLlmWithInitPrompt() {
            // given
            mockChatClientReturns("첫 요약 결과");

            // when
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);

            // then
            verify(reviewChatClient).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("LLM 응답으로 course.updateSummary()를 호출한다")
        void updatesCourseSummary() {
            // given
            String llmResponse = "첫 요약 결과";
            mockChatClientReturns(llmResponse);

            // when
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);

            // then
            verify(course).updateSummary(llmResponse);
        }
    }

    @Nested
    @DisplayName("기존 요약이 있을 때 (누적 갱신)")
    class WhenExistingSummaryExists {

        @BeforeEach
        void setUp() {
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(course.getSummary()).thenReturn(EXISTING_SUMMARY);
        }

        @Test
        @DisplayName("updatePrompt 템플릿으로 LLM을 호출한다")
        void callsLlmWithUpdatePrompt() {
            // given
            mockChatClientReturns("갱신된 요약 결과");

            // when
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);

            // then
            verify(reviewChatClient).prompt(any(Prompt.class));
        }

        @Test
        @DisplayName("LLM 응답으로 course.updateSummary()를 호출한다")
        void updatesCourseSummary() {
            // given
            String llmResponse = "갱신된 요약 결과";
            mockChatClientReturns(llmResponse);

            // when
            reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW);

            // then
            verify(course).updateSummary(llmResponse);
        }
    }

    @Nested
    @DisplayName("예외 상황")
    class WhenException {

        @Test
        @DisplayName("코스가 존재하지 않으면 ServiceErrorException을 던진다")
        void throwsWhenCourseNotFound() {
            // given
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> reviewSummaryService.updateSummary(COURSE_ID, NEW_REVIEW))
                    .isInstanceOf(ServiceErrorException.class);

            verify(reviewChatClient, never()).prompt(any(Prompt.class));
        }
    }
}