package com.intuit.fileUploadDemo.entities;

import com.intuit.fileUploadDemo.entities.enums.ChunkStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "upload_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chunks_file_chunk_index",
                columnNames = {"file_id", "chunk_index"}
        ),
        indexes = {
                @Index(name = "ix_chunks_file_id", columnList = "file_id"),
                @Index(name = "ix_chunks_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UploadChunk {

    @Id
    @Column(name = "id", nullable = false, length = 40)
    @EqualsAndHashCode.Include
    private String id; // e.g., "C1"

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private UploadFile file;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex; // 0-based index

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChunkStatus status = ChunkStatus.PENDING;

    // ETag returned by S3 for the uploaded part
    @Column(name = "etag", length = 128)
    private String etag;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;
}
