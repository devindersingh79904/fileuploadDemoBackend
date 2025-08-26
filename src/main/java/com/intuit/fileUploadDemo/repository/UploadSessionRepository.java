package com.intuit.fileUploadDemo.repository;

import com.intuit.fileUploadDemo.entities.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadSessionRepository extends JpaRepository<UploadSession,String> {

    Page<UploadSession> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Page<UploadSession> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, SessionStatus status, Pageable pageable);
    boolean existsByUserIdAndStatus(String userId, SessionStatus status);
}
