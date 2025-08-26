package com.intuit.fileUploadDemo.repository;

import com.intuit.fileUploadDemo.entities.UploadFile;
import com.intuit.fileUploadDemo.entities.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadFileRepository extends JpaRepository<UploadFile,String> {
    List<UploadFile> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<UploadFile> findBySessionIdAndStatus(String sessionId, FileStatus status);
    Optional<UploadFile> findBySessionIdAndFileName(String sessionId, String fileName);
    long countBySessionIdAndStatus(String sessionId, FileStatus status);

}
