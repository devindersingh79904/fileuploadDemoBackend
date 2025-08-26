package com.intuit.fileUploadDemo.repository;

import com.intuit.fileUploadDemo.entities.UploadChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadChunkRepository extends JpaRepository<UploadChunk,String> {
    List<UploadChunk> findByFileIdOrderByChunkIndexAsc(String fileId);
    List<UploadChunk> findByFileIdAndStatusOrderByChunkIndexAsc(String fileId, String status);
    Optional<UploadChunk> findByFileIdAndChunkIndex(String fileId, int chunkIndex);
    long countByFileIdAndStatus(String fileId, String status);
    boolean existsByFileIdAndChunkIndex(String fileId, int chunkIndex);
}
