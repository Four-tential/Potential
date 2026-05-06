package four_tential.potential.infra.ai.etl;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "policy_document",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_policy_document_source", columnNames = {"source"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyDocument extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    public static PolicyDocument of(String source, String contentHash, int chunkCount) {
        PolicyDocument doc = new PolicyDocument();
        doc.source = source;
        doc.contentHash = contentHash;
        doc.chunkCount = chunkCount;
        return doc;
    }

    public void update(String contentHash, int chunkCount) {
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
    }
}
