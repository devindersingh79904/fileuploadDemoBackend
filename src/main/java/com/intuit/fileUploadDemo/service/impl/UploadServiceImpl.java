package com.intuit.fileUploadDemo.service.impl;

import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.PresignPartUrlResponse;
import com.intuit.fileUploadDemo.dto.response.RegisterFileResponse;
import com.intuit.fileUploadDemo.dto.response.SessionStatusResponse;
import com.intuit.fileUploadDemo.dto.response.StartSessionResponse;
import com.intuit.fileUploadDemo.entities.UploadChunk;
import com.intuit.fileUploadDemo.entities.UploadFile;
import com.intuit.fileUploadDemo.entities.UploadSession;
import com.intuit.fileUploadDemo.entities.enums.ChunkStatus;
import com.intuit.fileUploadDemo.entities.enums.FileStatus;
import com.intuit.fileUploadDemo.entities.enums.SessionStatus;
import com.intuit.fileUploadDemo.repository.UploadChunkRepository;
import com.intuit.fileUploadDemo.repository.UploadFileRepository;
import com.intuit.fileUploadDemo.repository.UploadSessionRepository;
import com.intuit.fileUploadDemo.service.S3MultipartService;
import com.intuit.fileUploadDemo.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadFileRepository uploadFileRepository;
    private final UploadChunkRepository uploadChunkRepository;
    private final S3MultipartService multipartSvc;

    private String nextSessionId() {
        long n = uploadSessionRepository.count() + 1;
        return "S" + n;
    }

    private String nextFileId() {
        long n = uploadFileRepository.count() + 1;
        return "F" + n;
    }

    private String nextChunkId() {
        long n = uploadChunkRepository.count() + 1;
        return "C" + n;
    }

    private void ensureSessionMutable(UploadSession s) {
        if (s.getStatus() == SessionStatus.COMPLETED
                || s.getStatus() == SessionStatus.CANCELLED
                || s.getStatus() == SessionStatus.FAILED) {
            throw new IllegalStateException("Session is not mutable");
        }
    }

    private void ensureFileMutable(UploadFile f) {
        if (f.getStatus() == FileStatus.UPLOADED || f.getStatus() == FileStatus.FAILED) {
            throw new IllegalStateException("File is not mutable");
        }
    }

    // ───────────────────────────────────────────────────────────────
    // UPDATED: Start Session (idempotent per user)
    // ───────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public StartSessionResponse startSession(StartSessionRequest request) {
        String userId = request.getUserId();

        // Check if there's an existing IN_PROGRESS or PAUSED session for this user
        Optional<UploadSession> existing = uploadSessionRepository
                .findFirstByUserIdAndStatusIn(userId, Arrays.asList(
                        SessionStatus.IN_PROGRESS,
                        SessionStatus.PAUSED
                ));

        if (existing.isPresent()) {
            return new StartSessionResponse(existing.get().getId());
        }

        // Else create new session
        String sessionId = nextSessionId();
        UploadSession session = UploadSession.builder()
                .id(sessionId)
                .userId(userId)
                .status(SessionStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        uploadSessionRepository.save(session);
        return new StartSessionResponse(sessionId);
    }

    @Override
    @Transactional
    public RegisterFileResponse registerFile(String sessionId, RegisterFileRequest request) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found  " + sessionId));

        ensureSessionMutable(session);
        String fileId = nextFileId();
        String s3Key = sessionId + "/" + fileId + "/" + request.getFileName();

        String uploadId = multipartSvc.start(
                s3Key,
                "application/octet-stream"
        );

        UploadFile file = UploadFile.builder()
                .id(fileId)
                .session(session)
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .totalChunks(request.getChunkCount())
                .s3Key(s3Key)
                .uploadId(uploadId)
                .status(FileStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        uploadFileRepository.save(file);

        for (int i = 0; i < request.getChunkCount(); i++) {
            String chunkId = nextChunkId();
            UploadChunk chunk = UploadChunk.builder()
                    .id(chunkId)
                    .file(file)
                    .chunkIndex(i)
                    .status(ChunkStatus.PENDING)
                    .build();
            uploadChunkRepository.save(chunk);
        }

        return new RegisterFileResponse(fileId, s3Key, uploadId);
    }

    @Override
    @Transactional
    public PresignPartUrlResponse presignPartUrl(String fileId, PresignPartUrlRequest request) {
        UploadFile file = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));

        ensureFileMutable(file);

        if (file.getStatus() == FileStatus.PAUSED) {
            throw new IllegalStateException("File is paused. Resume before presigning parts.");
        }

        int partNumber = request.getPartNumber();
        if (partNumber < 1 || partNumber > file.getTotalChunks()) {
            throw new IllegalArgumentException("Invalid partNumber: " + partNumber);
        }

        int chunkIndex = partNumber - 1;
        UploadChunk chunk = uploadChunkRepository.findByFileIdAndChunkIndex(fileId, chunkIndex)
                .orElseThrow(() -> new NoSuchElementException("Chunk not found for part " + partNumber));

        String presigned = multipartSvc.presignPart(
                file.getS3Key(),
                file.getUploadId(),
                partNumber,
                0L
        );

        return new PresignPartUrlResponse(presigned);
    }

    @Override
    @Transactional
    public void completeFile(String fileId, CompleteFileRequest request) {
        UploadFile file = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));

        ensureFileMutable(file);
        if (!Objects.equals(file.getUploadId(), request.getUploadId())) {
            throw new IllegalArgumentException("uploadId mismatch for file " + fileId);
        }

        Map<Integer, String> partToEtag = request.getParts().stream()
                .peek(p -> {
                    if (p.getPartNumber() < 1 || p.getPartNumber() > file.getTotalChunks()) {
                        throw new IllegalArgumentException("Invalid partNumber: " + p.getPartNumber());
                    }
                })
                .collect(Collectors.toMap(CompleteFileRequest.PartETag::getPartNumber, CompleteFileRequest.PartETag::getETag));

        List<Map.Entry<Integer, String>> partEntries = partToEtag.entrySet().stream().toList();
        multipartSvc.complete(
                file.getS3Key(),
                file.getUploadId(),
                partEntries
        );

        List<UploadChunk> chunks = uploadChunkRepository.findByFileIdOrderByChunkIndexAsc(fileId);
        for (UploadChunk c : chunks) {
            int partNo = c.getChunkIndex() + 1;
            String etag = partToEtag.get(partNo);
            if (etag == null || etag.isBlank()) {
                throw new IllegalStateException("Missing ETag for partNumber " + partNo);
            }
            c.setEtag(etag);
            c.setStatus(ChunkStatus.UPLOADED);
            c.setUploadedAt(Instant.now());

            uploadChunkRepository.save(c);
        }

        file.setUploadedChunks(file.getTotalChunks());
        file.setStatus(FileStatus.UPLOADED);
        file.setUpdatedAt(Instant.now());
        uploadFileRepository.save(file);

        String sessionId = file.getSession().getId();
        List<UploadFile> filesInSession = uploadFileRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        boolean allUploaded = filesInSession.stream().allMatch(f -> f.getStatus() == FileStatus.UPLOADED);
        if (allUploaded) {
            UploadSession s = file.getSession();
            s.setStatus(SessionStatus.COMPLETED);
            s.setUpdatedAt(Instant.now());
            uploadSessionRepository.save(s);
        }
    }

    @Override
    @Transactional
    public SessionStatusResponse getSessionStatus(String sessionId) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        List<UploadFile> files = uploadFileRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        List<SessionStatusResponse.FileStatusItem> items = new ArrayList<>();
        for (UploadFile f : files) {
            List<UploadChunk> chunks = uploadChunkRepository.findByFileIdOrderByChunkIndexAsc(f.getId());
            List<Integer> pending = chunks.stream()
                    .filter(c -> c.getStatus() != ChunkStatus.UPLOADED)
                    .map(UploadChunk::getChunkIndex)
                    .toList();

            items.add(new SessionStatusResponse.FileStatusItem(
                    f.getId(),
                    f.getFileName(),
                    f.getTotalChunks(),
                    f.getUploadedChunks(),
                    f.getStatus(),
                    pending
            ));
        }

        return new SessionStatusResponse(session.getId(), items);
    }

    @Override
    @Transactional
    public void pauseSession(String sessionId) {
        UploadSession s = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        if (s.getStatus() == SessionStatus.COMPLETED
                || s.getStatus() == SessionStatus.FAILED
                || s.getStatus() == SessionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot pause session in status " + s.getStatus());
        }

        if (s.getStatus() == SessionStatus.PAUSED) return;

        s.setStatus(SessionStatus.PAUSED);
        s.setUpdatedAt(Instant.now());
        uploadSessionRepository.save(s);

        List<UploadFile> files = uploadFileRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        for (UploadFile f : files) {
            if (f.getStatus() == FileStatus.IN_PROGRESS) {
                f.setStatus(FileStatus.PAUSED);
                f.setUpdatedAt(Instant.now());
                uploadFileRepository.save(f);
            }
        }
    }

    @Override
    @Transactional
    public void resumeSession(String sessionId) {
        UploadSession s = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

        if (s.getStatus() == SessionStatus.COMPLETED
                || s.getStatus() == SessionStatus.FAILED
                || s.getStatus() == SessionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot resume session in status " + s.getStatus());
        }
        if (s.getStatus() == SessionStatus.IN_PROGRESS) return;

        s.setStatus(SessionStatus.IN_PROGRESS);
        s.setUpdatedAt(Instant.now());
        uploadSessionRepository.save(s);

        List<UploadFile> files = uploadFileRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        for (UploadFile f : files) {
            if (f.getStatus() == FileStatus.PAUSED) {
                f.setStatus(FileStatus.IN_PROGRESS);
                f.setUpdatedAt(Instant.now());
                uploadFileRepository.save(f);
            }
        }
    }

    @Override
    @Transactional
    public void pauseFile(String fileId) {
        UploadFile f = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));
        if (f.getStatus() == FileStatus.UPLOADED || f.getStatus() == FileStatus.FAILED) {
            throw new IllegalStateException("Cannot pause file in status " + f.getStatus());
        }
        if (f.getStatus() == FileStatus.PAUSED) return;

        f.setStatus(FileStatus.PAUSED);
        f.setUpdatedAt(Instant.now());
        uploadFileRepository.save(f);
    }

    @Override
    @Transactional
    public void resumeFile(String fileId) {
        UploadFile f = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));
        if (f.getStatus() == FileStatus.UPLOADED || f.getStatus() == FileStatus.FAILED) {
            throw new IllegalStateException("Cannot resume file in status " + f.getStatus());
        }
        if (f.getStatus() == FileStatus.IN_PROGRESS) return;

        f.setStatus(FileStatus.IN_PROGRESS);
        f.setUpdatedAt(Instant.now());
        uploadFileRepository.save(f);
    }
}
