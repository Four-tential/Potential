package four_tential.potential.infra.ai.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 범용 벡터 저장소 서비스
 *
 * 도메인에 관계없이 텍스트 임베딩 저장/검색/삭제 기능을 제공합니다.
 * 도메인 구분은 메타데이터(domain, entityId)로 처리합니다.
 *
 * 사용 예시:
 *   // 저장
 *   vectorStoreService.add("review", courseId, content);
 *   vectorStoreService.addBatch("review", courseId, contents);
 *
 *   // 검색
 *   vectorStoreService.search("review", courseId, query);
 *
 *   // 삭제
 *   vectorStoreService.delete("review", courseId);
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private static final int DEFAULT_TOP_K = 5;  //검색 결과 최대 개수
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;   //유사도 필터링

    private final VectorStore vectorStore;

    //  저장
    /**
     * 단건 텍스트를 벡터 저장소에 저장
     *
     * @param domain   도메인 구분자 (예: "review", "course", "instructor")
     * @param entityId 엔티티 ID (예: courseId, memberId)
     * @param content  저장할 텍스트
     */
    public void add(String domain, Long entityId, String content) {
        Document document = buildDocument(domain, entityId, content);
        vectorStore.add(List.of(document));
        log.debug("벡터 저장 완료 — domain: {}, entityId: {}", domain, entityId);
    }

    /**
     * 여러 텍스트를 한 번에 벡터 저장소에 저장 (배치)
     *
     * @param domain   도메인 구분자
     * @param entityId 엔티티 ID
     * @param contents 저장할 텍스트 목록
     */
    public void addBatch(String domain, Long entityId, List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        List<Document> documents = contents.stream()
                .map(content -> buildDocument(domain, entityId, content))
                .toList();
        vectorStore.add(documents);
        log.debug("벡터 배치 저장 완료 — domain: {}, entityId: {}, 건수: {}", domain, entityId, documents.size());
    }

    //  검색
    /**
     * 유사도 검색 — domain + entityId 필터 적용
     *
     * @param domain   도메인 구분자
     * @param entityId 엔티티 ID
     * @param query    검색 질의 텍스트
     * @return 유사 텍스트 목록
     */
    public List<String> search(String domain, Long entityId, String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression("domain == '" + domain + "' && entityId == " + entityId)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .map(Document::getText)
                .toList();
    }

    /**
     * 유사도 검색 — domain 필터만 적용 (entityId 무관)
     *
     * @param domain 도메인 구분자
     * @param query  검색 질의 텍스트
     * @return 유사 텍스트 목록
     */
    public List<String> searchByDomain(String domain, String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression("domain == '" + domain + "'")
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .map(Document::getText)
                .toList();
    }

    //  삭제
    /**
     * domain + entityId 조건으로 벡터 삭제
     *
     * @param domain   도메인 구분자
     * @param entityId 엔티티 ID
     */
    public void delete(String domain, Long entityId) {
        vectorStore.delete("domain == '" + domain + "' && entityId == " + entityId);
        log.debug("벡터 삭제 완료 — domain: {}, entityId: {}", domain, entityId);
    }

    //  내부 유틸
    private Document buildDocument(String domain, Long entityId, String content) {
        return new Document(content, Map.of(
                "domain", domain,
                "entityId", entityId
        ));
    }
}