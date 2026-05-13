package four_tential.potential.infra.ai.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class VectorStoreService {

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;

    private final VectorStore vectorStore;

    public void add(String domain, Long entityId, String content) {
        Document document = buildDocument(domain, entityId, content);
        vectorStore.add(List.of(document));
        log.debug("벡터 저장 완료 — domain: {}, entityId: {}", domain, entityId);
    }

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

    public List<String> search(String domain, Long entityId, String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression(buildFilter(domain, entityId))
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .map(Document::getText)
                .toList();
    }

    public List<String> searchByDomain(String domain, String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression(buildDomainFilter(domain))
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .map(Document::getText)
                .toList();
    }

    public void delete(String domain, Long entityId) {
        vectorStore.delete(buildFilter(domain, entityId));
        log.debug("벡터 삭제 완료 — domain: {}, entityId: {}", domain, entityId);
    }

    private String buildFilter(String domain, Long entityId) {
        return "domain == '" + domain + "' && entityId == " + entityId;
    }

    private String buildDomainFilter(String domain) {
        return "domain == '" + domain + "'";
    }

    private Document buildDocument(String domain, Long entityId, String content) {
        return new Document(content, Map.of(
                "domain", domain,
                "entityId", entityId
        ));
    }
}
