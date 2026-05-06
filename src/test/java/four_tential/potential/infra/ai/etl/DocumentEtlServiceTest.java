package four_tential.potential.infra.ai.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentEtlServiceTest {

    private VectorStore vectorStore;
    private PolicyDocumentRepository policyDocumentRepository;
    private DocumentEtlService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class, RETURNS_DEEP_STUBS);
        policyDocumentRepository = mock(PolicyDocumentRepository.class);
        when(policyDocumentRepository.findAll()).thenReturn(List.of());
        service = new DocumentEtlService(vectorStore, policyDocumentRepository);
    }

    @Test
    @DisplayName("loadPolicyDocuments — 빈 DB 상태에서는 모든 파일이 added 로 적재")
    void load_with_empty_db_adds_all() {
        ReloadResult result = service.loadPolicyDocuments();

        assertThat(result.added()).isPositive();
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
        assertThat(result.removed()).isZero();
        assertThat(result.totalChunks()).isPositive();

        verify(vectorStore, times(result.added())).add(argThat((List<Document> docs) -> {
            assertThat(docs).isNotEmpty();
            Document first = docs.get(0);
            assertThat(first.getMetadata())
                    .containsEntry("domain", "policy")
                    .containsKey("source")
                    .containsKey("section")
                    .containsKey("title");
            return true;
        }));
        verify(policyDocumentRepository, times(result.added())).save(any(PolicyDocument.class));
        verify(vectorStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("loadPolicyDocuments — 모든 hash 일치 시 임베딩 호출 없이 unchanged")
    void load_with_matching_hashes_skips_embedding() {
        ArgumentCaptor<PolicyDocument> savedCaptor = ArgumentCaptor.forClass(PolicyDocument.class);
        when(policyDocumentRepository.save(savedCaptor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        ReloadResult firstRun = service.loadPolicyDocuments();
        List<PolicyDocument> persisted = new ArrayList<>(savedCaptor.getAllValues());

        reset(vectorStore, policyDocumentRepository);
        when(policyDocumentRepository.findAll()).thenReturn(persisted);

        ReloadResult secondRun = service.loadPolicyDocuments();

        assertThat(secondRun.unchanged()).isEqualTo(firstRun.added());
        assertThat(secondRun.added()).isZero();
        assertThat(secondRun.updated()).isZero();
        assertThat(secondRun.removed()).isZero();

        verify(vectorStore, never()).add(any());
        verify(vectorStore, never()).delete(anyString());
        verify(policyDocumentRepository, never()).save(any());
        verify(policyDocumentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("loadPolicyDocuments — 디스크에 없는 source 가 DB 에 있으면 vector 청크 + 메타 모두 삭제")
    void load_removes_orphan_meta_for_missing_disk_files() {
        PolicyDocument orphan = PolicyDocument.of("ghost-policy", "deadbeef", 5);
        when(policyDocumentRepository.findAll()).thenReturn(List.of(orphan));

        ReloadResult result = service.loadPolicyDocuments();

        assertThat(result.removed()).isEqualTo(1);
        verify(vectorStore).delete(contains("source == 'ghost-policy'"));
        verify(policyDocumentRepository).delete(orphan);
    }

    @Test
    @DisplayName("loadPolicyDocuments — DB hash 불일치 시 해당 source 만 삭제 후 재적재")
    void load_with_stale_hash_updates_only_that_source() {
        PolicyDocument staleMeta = PolicyDocument.of("common-policy", "stale-hash", 3);
        when(policyDocumentRepository.findAll()).thenReturn(List.of(staleMeta));

        ReloadResult result = service.loadPolicyDocuments();

        assertThat(result.updated()).isEqualTo(1);
        verify(vectorStore).delete(contains("source == 'common-policy'"));
        verify(vectorStore, never()).delete(contains("source == 'order-policy'"));
        assertThat(staleMeta.getContentHash()).isNotEqualTo("stale-hash");
    }

    @Test
    @DisplayName("loadPolicyDocuments — 청크 삭제 실패 시 IllegalStateException")
    void load_aborts_when_vector_delete_fails() {
        PolicyDocument staleMeta = PolicyDocument.of("common-policy", "stale-hash", 3);
        when(policyDocumentRepository.findAll()).thenReturn(List.of(staleMeta));
        doThrow(new RuntimeException("벡터 저장소 일시 오류"))
                .when(vectorStore).delete(anyString());

        assertThatThrownBy(() -> service.loadPolicyDocuments())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("벡터 청크 삭제 실패");
    }

    @Test
    @DisplayName("extractTitle — '# 제목' 으로 시작하면 제목 추출")
    void extractTitle_with_title_prefix() throws Exception {
        String result = invokeExtractTitle("# 환불 정책\n## 본문");
        assertThat(result).isEqualTo("환불 정책");
    }

    @Test
    @DisplayName("extractTitle — '# ' 가 없으면 unknown 반환")
    void extractTitle_without_prefix_returns_unknown() throws Exception {
        String result = invokeExtractTitle("본문만 있음");
        assertThat(result).isEqualTo("unknown");
    }

    @Test
    @DisplayName("splitByMarkdownHeader — 빈 섹션은 스킵, 본문 있는 섹션만 청크화")
    void splitByMarkdownHeader_skips_empty_sections() throws Exception {
        String content = """
                # 테스트 정책
                ## 빈 섹션
                ## 정상 섹션
                실제 내용입니다.
                """;
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) invokeSplit(content, "test-policy");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getMetadata()).containsEntry("section", "정상 섹션");
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "test-policy");
    }

    @Test
    @DisplayName("splitByMarkdownHeader — source 가 null 이면 unknown")
    void splitByMarkdownHeader_null_source() throws Exception {
        String content = """
                # 타이틀
                ## 섹션
                본문
                """;
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) invokeSplit(content, null);

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "unknown");
    }

    private String invokeExtractTitle(String content) throws Exception {
        Method method = DocumentEtlService.class.getDeclaredMethod("extractTitle", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, content);
    }

    private Object invokeSplit(String content, String source) throws Exception {
        Method method = DocumentEtlService.class.getDeclaredMethod(
                "splitByMarkdownHeader", String.class, String.class);
        method.setAccessible(true);
        return method.invoke(service, content, source);
    }
}
