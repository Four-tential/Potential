package four_tential.potential.infra.ai.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * ReviewEmbeddingService 단위 테스트
 *
 * VectorStore를 Mock으로 처리 — 실제 pgvector/OpenAI 호출 없음
 * 검증 대상: 저장 시 메타데이터 구성, 검색 시 필터 조건, 빈 결과 처리
 */
class ReviewEmbeddingServiceTest {

    private VectorStore vectorStore;
    private ReviewEmbeddingService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        service = new ReviewEmbeddingService(vectorStore);
    }

    // ─────────────────────────────────────────
    //  저장 (임베딩)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("리뷰 임베딩 저장 시 domain, courseId 메타데이터가 포함된 Document를 저장")
    void embed_review_stores_document_with_metadata() {
        // given
        Long courseId = 1L;
        String reviewContent = "정말 유익한 수업이었습니다.";

        // when
        service.embedReview(courseId, reviewContent);

        // then — vectorStore.add()에 올바른 메타데이터가 담긴 Document가 전달됐는지 검증
        verify(vectorStore).add(argThat(docs -> {
            assertThat(docs).hasSize(1);
            Document doc = docs.get(0);
            assertThat(doc.getText()).isEqualTo(reviewContent);
            assertThat(doc.getMetadata()).containsEntry("domain", "review");
            assertThat(doc.getMetadata()).containsEntry("courseId", courseId);
            return true;
        }));
    }

    @Test
    @DisplayName("여러 리뷰를 한 번에 임베딩 저장")
    void embed_reviews_batch() {
        // given
        Long courseId = 2L;
        List<String> reviews = List.of("좋아요", "별로예요", "강력 추천");

        // when
        service.embedReviews(courseId, reviews);

        // then
        verify(vectorStore).add(argThat(docs -> {
            assertThat(docs).hasSize(3);
            docs.forEach(doc ->
                    assertThat(doc.getMetadata()).containsEntry("courseId", courseId)
            );
            return true;
        }));
    }

    // ─────────────────────────────────────────
    //  검색 (유사도)
    // ─────────────────────────────────────────

    @Test
    @DisplayName("코스 ID로 유사 리뷰 검색 — 결과 반환")
    void search_similar_reviews_returns_results() {
        // given
        Long courseId = 1L;
        String query = "강사님 설명이 친절한가요?";

        List<Document> mockDocs = List.of(
                new Document("설명이 정말 친절했어요.", Map.of("domain", "review", "courseId", courseId)),
                new Document("강사님이 질문에 잘 답해주세요.", Map.of("domain", "review", "courseId", courseId))
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocs);

        // when
        List<String> results = service.searchSimilarReviews(courseId, query);

        // then
        assertThat(results).hasSize(2);
        assertThat(results).contains("설명이 정말 친절했어요.", "강사님이 질문에 잘 답해주세요.");
    }

    @Test
    @DisplayName("유사 리뷰가 없으면 빈 리스트 반환")
    void search_similar_reviews_returns_empty_when_no_result() {
        // given
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // when
        List<String> results = service.searchSimilarReviews(1L, "아무 질문");

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("검색 시 courseId 필터와 domain=review 필터가 적용됨")
    void search_applies_domain_and_courseId_filter() {
        // given
        Long courseId = 3L;
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // when
        service.searchSimilarReviews(courseId, "검색어");

        // then — SearchRequest에 필터가 포함되었는지 검증
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) -> {
            String filter = String.valueOf(request.getFilterExpression());
            assertThat(filter).contains("domain");
            assertThat(filter).contains("review");
            assertThat(filter).contains(courseId.toString());
            return true;
        }));
    }
}