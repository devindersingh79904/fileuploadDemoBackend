package com.intuit.fileUploadDemo.controller;

import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.PresignPartUrlResponse;
import com.intuit.fileUploadDemo.dto.response.RegisterFileResponse;
import com.intuit.fileUploadDemo.dto.response.SessionStatusResponse;
import com.intuit.fileUploadDemo.dto.response.StartSessionResponse;
import com.intuit.fileUploadDemo.service.UploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    // Start or reuse a session for a user
    @PostMapping("/start")
    public ResponseEntity<StartSessionResponse> startSession(@Valid @RequestBody StartSessionRequest request) {
        StartSessionResponse response = uploadService.startSession(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // Register a file under a session
    @PostMapping("/{sessionId}/files")
    public ResponseEntity<RegisterFileResponse> registerFile(@PathVariable String sessionId,
                                                             @Valid @RequestBody RegisterFileRequest request) {
        RegisterFileResponse response = uploadService.registerFile(sessionId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // Get a presigned URL for a specific part of a file
    @PostMapping("/{fileId}/parts/url")
    public ResponseEntity<PresignPartUrlResponse> presignPartUrl(@PathVariable String fileId,
                                                                 @Valid @RequestBody PresignPartUrlRequest req) {
        PresignPartUrlResponse body = uploadService.presignPartUrl(fileId, req);
        return ResponseEntity.ok(body);
    }

    // Complete a file (send all partNumber + eTag pairs)
    @PatchMapping("/{fileId}/complete")
    public ResponseEntity<Void> completeFile(@PathVariable String fileId,
                                             @Valid @RequestBody CompleteFileRequest req) {
        uploadService.completeFile(fileId, req);
        return ResponseEntity.noContent().build();
    }

    // Get status of a session
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<SessionStatusResponse> sessionStatus(@PathVariable String sessionId) {
        SessionStatusResponse body = uploadService.getSessionStatus(sessionId);
        return ResponseEntity.ok(body);
    }

    // Pause / resume a session
    @PatchMapping("/{sessionId}/pause")
    public ResponseEntity<Void> pauseSession(@PathVariable String sessionId) {
        uploadService.pauseSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{sessionId}/resume")
    public ResponseEntity<Void> resumeSession(@PathVariable String sessionId) {
        uploadService.resumeSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    // Pause / resume a single file
    @PatchMapping("/files/{fileId}/pause")
    public ResponseEntity<Void> pauseFile(@PathVariable String fileId) {
        uploadService.pauseFile(fileId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/files/{fileId}/resume")
    public ResponseEntity<Void> resumeFile(@PathVariable String fileId) {
        uploadService.resumeFile(fileId);
        return ResponseEntity.noContent().build();
    }

    // NEW: Explicitly complete (finalize) the session after all files are uploaded
    @PatchMapping("/{sessionId}/complete")
    public ResponseEntity<Void> completeSession(@PathVariable String sessionId) {
        uploadService.completeSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
