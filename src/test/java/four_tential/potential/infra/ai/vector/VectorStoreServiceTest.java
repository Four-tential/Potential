package four_tential.potential.infra.ai.vector;

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
 * VectorStoreService 단위 테스트
 *
 * VectorStore Mock 처리 — 실제 pgvector/임베딩 모델 호출 없음
 */
class VectorStoreServiceTest {

    private VectorStore vectorStore;
    private VectorStoreService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        service = new VectorStoreService(vectorStore);
    }

    // ─────────────────────────────────────────
    //  저장
    // ─────────────────────────────────────────

    @Test
    @DisplayName("단건 저장 시 domain, entityId 메타데이터가 포함된 Document 저장")
    void add_stores_document_with_correct_metadata() {
        // given
        String domain = "review";
        Long entityId = 1L;
        String content = "Great class";

        // when
        service.add(domain, entityId, content);

        // then
        verify(vectorStore).add(argThat(docs -> {
            assertThat(docs).hasSize(1);
            Document doc = docs.get(0);
            assertThat(doc.getText()).isEqualTo(content);
            assertThat(doc.getMetadata()).containsEntry("domain", domain);
            assertThat(doc.getMetadata()).containsEntry("entityId", entityId);
            return true;
        }));
    }

    @Test
    @DisplayName("배치 저장 시 모든 Document에 동일한 domain, entityId 메타데이터 포함")
    void addBatch_stores_all_documents_with_metadata() {
        // given
        String domain = "course";
        Long entityId = 2L;
        List<String> contents = List.of("Intro to Java", "Spring Boot basics", "JPA fundamentals");

        // when
        service.addBatch(domain, entityId, contents);

        // then
        verify(vectorStore).add(argThat(docs -> {
            assertThat(docs).hasSize(3);
            docs.forEach(doc -> {
                assertThat(doc.getMetadata()).containsEntry("domain", domain);
                assertThat(doc.getMetadata()).containsEntry("entityId", entityId);
            });
            return true;
        }));
    }

    @Test
    @DisplayName("빈 리스트 배치 저장 시 vectorStore.add() 호출하지 않음")
    void addBatch_does_nothing_when_empty() {
        // when
        service.addBatch("review", 1L, List.of());

        // then
        verify(vectorStore, never()).add(any());
    }

    // ─────────────────────────────────────────
    //  검색
    // ─────────────────────────────────────────

    @Test
    @DisplayName("search 시 domain + entityId 필터가 적용된 SearchRequest 전달")
    void search_applies_domain_and_entityId_filter() {
        // given
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // when
        service.search("review", 1L, "helpful instructor");

        // then
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) -> {
            String filter = String.valueOf(request.getFilterExpression());
            assertThat(filter).contains("domain");
            assertThat(filter).contains("review");
            assertThat(filter).contains("entityId");
            assertThat(filter).contains("1");
            return true;
        }));
    }

    @Test
    @DisplayName("검색 결과를 텍스트 목록으로 반환")
    void search_returns_text_list() {
        // given
        List<Document> mockDocs = List.of(
                new Document("Great class", Map.of("domain", "review", "entityId", 1L)),
                new Document("Very helpful", Map.of("domain", "review", "entityId", 1L))
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocs);

        // when
        List<String> results = service.search("review", 1L, "helpful");

        // then
        assertThat(results).containsExactly("Great class", "Very helpful");
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트 반환")
    void search_returns_empty_when_no_result() {
        // given
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // when
        List<String> results = service.search("review", 1L, "query");

        // then
        assertThat(results).isEmpty();
    }

    // ─────────────────────────────────────────
    //  삭제
    // ─────────────────────────────────────────

    @Test
    @DisplayName("삭제 시 domain + entityId 필터 표현식으로 vectorStore.delete() 호출")
    void delete_calls_vectorStore_with_filter() {
        // when
        service.delete("review", 1L);

        // then — vectorStore.delete()에 올바른 필터 문자열이 전달됐는지 검증
        verify(vectorStore).delete(argThat((String filter) -> {
            assertThat(filter).contains("domain");
            assertThat(filter).contains("review");
            assertThat(filter).contains("entityId");
            assertThat(filter).contains("1");
            return true;
        }));
    }

    // ─────────────────────────────────────────
//  searchByDomain
// ─────────────────────────────────────────

    @Test
    @DisplayName("searchByDomain 시 domain 필터만 적용된 SearchRequest 전달")
    void searchByDomain_applies_domain_filter_only() {
        // given
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // when
        service.searchByDomain("course", "Java 기초");

        // then
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) -> {
            String filter = String.valueOf(request.getFilterExpression());
            assertThat(filter).contains("domain");
            assertThat(filter).contains("course");
            assertThat(filter).doesNotContain("entityId");
            return true;
        }));
    }

    @Test
    @DisplayName("searchByDomain 검색 결과를 텍스트 목록으로 반환")
    void searchByDomain_returns_text_list() {
        // given
        List<Document> mockDocs = List.of(
                new Document("Java 기초 강의", Map.of("domain", "course")),
                new Document("Spring Boot 입문", Map.of("domain", "course"))
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocs);

        // when
        List<String> results = service.searchByDomain("course", "Java");

        // then
        assertThat(results).containsExactly("Java 기초 강의", "Spring Boot 입문");
    }

    @Test
    @DisplayName("searchByDomain 결과가 없으면 빈 리스트 반환")
    void searchByDomain_returns_empty_when_no_result() {
        // given
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // when
        List<String> results = service.searchByDomain("course", "없는내용");

        // then
        assertThat(results).isEmpty();
    }
}