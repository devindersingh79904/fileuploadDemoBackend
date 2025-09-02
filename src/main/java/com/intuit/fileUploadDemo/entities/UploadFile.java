package com.intuit.fileUploadDemo.entities;


import com.intuit.fileUploadDemo.entities.enums.FileStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "upload_files",
        indexes = {
                @Index(name = "ix_files_session_id", columnList = "session_id"),
                @Index(name = "ix_files_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UploadFile {
    @Id
    @Column(name = "id", nullable = false, length = 40)
    @EqualsAndHashCode.Include
    private String id; // e.g., "F1"

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private UploadSession session;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Builder.Default
    @Column(name = "uploaded_chunks", nullable = false)
    private int uploadedChunks = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FileStatus status = FileStatus.PENDING;

    // S3 multipart metadata (kept simple)
    @Column(name = "s3_bucket", length = 255)
    private String s3Bucket;

    @Column(name = "s3_key", length = 1024)
    private String s3Key;

    @Column(name = "upload_id", length = 255)
    private String uploadId;

    @Builder.Default
    @Column(name = "created_at", nullable = false,updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
