package four_tential.potential.infra.ai.etl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentEtlServiceTest {

    private VectorStore vectorStore;
    private DocumentEtlService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class, RETURNS_DEEP_STUBS);
        service = new DocumentEtlService(vectorStore);
    }

    @Test
    @DisplayName("loadPolicyDocuments — 클래스패스 md 파일 청킹 후 vectorStore 에 적재")
    void loadPolicyDocuments_chunks_and_adds() {
        int chunkCount = service.loadPolicyDocuments();

        assertThat(chunkCount).isPositive();
        verify(vectorStore).delete("domain == 'policy'");
        verify(vectorStore).add(argThat((List<Document> docs) -> {
            assertThat(docs).isNotEmpty();
            Document first = docs.get(0);
            assertThat(first.getMetadata())
                    .containsEntry("domain", "policy")
                    .containsKey("source")
                    .containsKey("section")
                    .containsKey("title");
            assertThat(first.getText()).contains(" > ");
            return true;
        }));
    }

    @Test
    @DisplayName("loadPolicyDocuments — 기존 삭제가 실패해도 신규 적재는 진행")
    void loadPolicyDocuments_proceeds_when_delete_fails() {
        doThrow(new RuntimeException("벡터 저장소 일시 오류"))
                .when(vectorStore).delete(anyString());

        int chunkCount = service.loadPolicyDocuments();

        assertThat(chunkCount).isPositive();
        verify(vectorStore, atLeastOnce()).add(any());
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
    @DisplayName("extractTitle — 개행이 없는 단일 라인 타이틀도 처리")
    void extractTitle_single_line() throws Exception {
        String result = invokeExtractTitle("# 단일 라인 타이틀");
        assertThat(result).isEqualTo("단일 라인 타이틀");
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
        List<Document> docs = (List<Document>) invokeSplit(content, "test-policy.md");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getMetadata()).containsEntry("section", "정상 섹션");
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "test-policy");
    }

    @Test
    @DisplayName("splitByMarkdownHeader — filename 이 null 이면 source 는 unknown")
    void splitByMarkdownHeader_null_filename() throws Exception {
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

    private Object invokeSplit(String content, String filename) throws Exception {
        Method method = DocumentEtlService.class.getDeclaredMethod(
                "splitByMarkdownHeader", String.class, String.class);
        method.setAccessible(true);
        return method.invoke(service, content, filename);
    }
}
