package com.intuit.fileUploadDemo.entities;

import com.intuit.fileUploadDemo.entities.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "upload_sessions",
        indexes = {
                @Index(name = "ix_sessions_user_id", columnList = "user_id"),
                @Index(name = "ix_sessions_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UploadSession {

    @Id
    @Column(name = "id", nullable = false,length = 40)
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "created_at", nullable = false,updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
