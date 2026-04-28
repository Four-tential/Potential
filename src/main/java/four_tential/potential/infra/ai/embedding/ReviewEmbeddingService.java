package four_tential.potential.infra.ai.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 리뷰 임베딩 서비스
 *
 * 리뷰 텍스트를 벡터로 변환하여 potential_vector_store에 저장하고,
 * 유사도 검색을 통해 관련 리뷰를 조회합니다.
 *
 * 도메인 구분: Document 메타데이터의 domain="review", courseId 필드로 처리
 * VectorStore는 단일 공유 저장소 — AiConfig#vectorStore Bean 주입
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewEmbeddingService {

    private static final String DOMAIN = "review";
    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;

    /**
     * 단일 리뷰를 벡터 저장소에 저장
     *
     * @param courseId      코스 ID (메타데이터 필터용)
     * @param reviewContent 리뷰 텍스트
     */
    public void embedReview(Long courseId, String reviewContent) {
        Document document = buildDocument(courseId, reviewContent);
        vectorStore.add(List.of(document));
        log.debug("리뷰 임베딩 저장 완료 — courseId: {}", courseId);
    }

    /**
     * 여러 리뷰를 한 번에 벡터 저장소에 저장 (배치)
     *
     * @param courseId 코스 ID
     * @param reviews  리뷰 텍스트 목록
     */
    public void embedReviews(Long courseId, List<String> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        List<Document> documents = reviews.stream()
                .map(content -> buildDocument(courseId, content))
                .toList();
        vectorStore.add(documents);
        log.debug("리뷰 배치 임베딩 저장 완료 — courseId: {}, 건수: {}", courseId, documents.size());
    }

    /**
     * 질의와 유사한 리뷰 검색
     *
     * @param courseId 코스 ID (해당 코스 리뷰만 검색)
     * @param query    검색 질의 텍스트
     * @return 유사 리뷰 텍스트 목록
     */
    public List<String> searchSimilarReviews(Long courseId, String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .filterExpression("domain == 'review' && courseId == " + courseId)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .map(Document::getText)
                .toList();
    }

    // ─────────────────────────────────────────
    //  내부 유틸
    // ─────────────────────────────────────────

    private Document buildDocument(Long courseId, String content) {
        return new Document(content, Map.of(
                "domain", DOMAIN,
                "courseId", courseId
        ));
    }
}