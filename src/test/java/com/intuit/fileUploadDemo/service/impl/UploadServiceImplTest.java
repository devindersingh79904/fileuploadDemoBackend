package com.intuit.fileUploadDemo.service.impl;


import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.RegisterFileResponse;
import com.intuit.fileUploadDemo.dto.response.SessionStatusResponse;
import com.intuit.fileUploadDemo.dto.response.StartSessionResponse;
import com.intuit.fileUploadDemo.entities.UploadChunk;
import com.intuit.fileUploadDemo.entities.UploadFile;
import com.intuit.fileUploadDemo.entities.UploadSession;
import com.intuit.fileUploadDemo.entities.enums.ChunkStatus;
import com.intuit.fileUploadDemo.entities.enums.FileStatus;
import com.intuit.fileUploadDemo.entities.enums.SessionStatus;
import com.intuit.fileUploadDemo.exception.ResourceNotFoundException;
import com.intuit.fileUploadDemo.repository.UploadChunkRepository;
import com.intuit.fileUploadDemo.repository.UploadFileRepository;
import com.intuit.fileUploadDemo.repository.UploadSessionRepository;
import com.intuit.fileUploadDemo.service.S3MultipartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock UploadSessionRepository sessionRepo;
    @Mock UploadFileRepository fileRepo;
    @Mock UploadChunkRepository chunkRepo;
    @Mock S3MultipartService s3;

    @InjectMocks UploadServiceImpl service;

    @Captor ArgumentCaptor<UploadSession> sessionCaptor;
    @Captor ArgumentCaptor<UploadFile> fileCaptor;
    @Captor ArgumentCaptor<UploadChunk> chunkCaptor;

    private UploadSession newSession(String id, String userId, SessionStatus st) {
        return UploadSession.builder()
                .id(id).userId(userId)
                .status(st)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private UploadFile newFile(String id, UploadSession s, String name, int chunks, FileStatus st, String key, String uploadId) {
        return UploadFile.builder()
                .id(id).session(s)
                .fileName(name)
                .fileSize(5_242_880L)
                .totalChunks(chunks)
                .uploadedChunks(0)
                .status(st)
                .s3Key(key)
                .uploadId(uploadId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setup() {
        // no-op
    }

    @Test
    void startSession_returnsExistingIfInProgressOrPaused() {
        // existing session
        UploadSession existing = newSession("S_EXIST", "USER1", SessionStatus.IN_PROGRESS);
        when(sessionRepo.findFirstByUserIdAndStatusIn(eq("USER1"), anyList()))
                .thenReturn(Optional.of(existing));

        StartSessionResponse resp = service.startSession(new StartSessionRequest("USER1"));

        assertThat(resp.getSessionId()).isEqualTo("S_EXIST");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void startSession_createsNewWhenNoneOpen() {
        when(sessionRepo.findFirstByUserIdAndStatusIn(eq("USER2"), anyList()))
                .thenReturn(Optional.empty());

        // capture saved session
        doAnswer(inv -> {
            UploadSession s = inv.getArgument(0);
            assertThat(s.getId()).startsWith("S");
            return s;
        }).when(sessionRepo).save(any(UploadSession.class));

        StartSessionResponse resp = service.startSession(new StartSessionRequest("USER2"));

        assertThat(resp.getSessionId()).startsWith("S");
        verify(sessionRepo).save(any(UploadSession.class));
    }

    @Test
    void registerFile_createsChunks_andStartsS3Upload() {
        UploadSession sess = newSession("S1", "U", SessionStatus.IN_PROGRESS);
        when(sessionRepo.findById("S1")).thenReturn(Optional.of(sess));
        when(s3.start(anyString(), any())).thenReturn("UPLOAD_123");

        RegisterFileRequest req = new RegisterFileRequest("file1.txt", 5_242_880L, 3);
        RegisterFileResponse resp = service.registerFile("S1", req);

        assertThat(resp.getFileId()).startsWith("F");
        assertThat(resp.getS3Key()).contains("S1/" + resp.getFileId() + "/file1.txt");
        assertThat(resp.getUploadId()).isEqualTo("UPLOAD_123");

        verify(fileRepo).save(fileCaptor.capture());
        UploadFile saved = fileCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(FileStatus.IN_PROGRESS);
        assertThat(saved.getTotalChunks()).isEqualTo(3);

        verify(chunkRepo, times(3)).save(any(UploadChunk.class));
        verify(s3).start(contains("S1/" + resp.getFileId() + "/file1.txt"), eq("application/octet-stream"));
    }

    @Test
    void presignPartUrl_throws_ifFilePaused() {
        UploadSession sess = newSession("S1", "U", SessionStatus.IN_PROGRESS);
        UploadFile f = newFile("F1", sess, "a.txt", 2, FileStatus.PAUSED, "S1/F1/a.txt", "U1");
        when(fileRepo.findById("F1")).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.presignPartUrl("F1", new com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paused");
    }

    @Test
    void completeFile_marksUploaded_andDoesNotAutoCompleteSession() {
        UploadSession sess = newSession("S1", "U", SessionStatus.IN_PROGRESS);
        UploadFile f = newFile("F1", sess, "a.txt", 2, FileStatus.IN_PROGRESS, "S1/F1/a.txt", "UPLOAD_1");

        when(fileRepo.findById("F1")).thenReturn(Optional.of(f));
        when(chunkRepo.findByFileIdOrderByChunkIndexAsc("F1")).thenReturn(List.of(
                UploadChunk.builder().id("C1").file(f).chunkIndex(0).status(ChunkStatus.PENDING).build(),
                UploadChunk.builder().id("C2").file(f).chunkIndex(1).status(ChunkStatus.PENDING).build()
        ));

        // mock s3 complete ok
        doNothing().when(s3).complete(eq("S1/F1/a.txt"), eq("UPLOAD_1"), anyList());

        CompleteFileRequest req = new CompleteFileRequest(
                "UPLOAD_1",
                List.of(
                        new CompleteFileRequest.PartETag(1, "etag-1"),
                        new CompleteFileRequest.PartETag(2, "etag-2")
                )
        );

        service.completeFile("F1", req);

        // file updated
        verify(fileRepo, atLeastOnce()).save(fileCaptor.capture());
        UploadFile savedFile = fileCaptor.getAllValues().get(fileCaptor.getAllValues().size()-1);
        assertThat(savedFile.getStatus()).isEqualTo(FileStatus.UPLOADED);
        assertThat(savedFile.getUploadedChunks()).isEqualTo(2);

        // session NOT auto-completed
        assertThat(sess.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(sessionRepo, atLeastOnce()).save(any(UploadSession.class)); // updatedAt touched
    }

    @Test
    void getSessionStatus_includesSessionStatusAndFiles() {
        UploadSession sess = newSession("S1", "U", SessionStatus.PAUSED);
        when(sessionRepo.findById("S1")).thenReturn(Optional.of(sess));

        UploadFile f1 = newFile("F1", sess, "a.txt", 3, FileStatus.IN_PROGRESS, "S1/F1/a.txt", "U1");
        f1.setUploadedChunks(1);
        UploadFile f2 = newFile("F2", sess, "b.txt", 1, FileStatus.UPLOADED, "S1/F2/b.txt", "U2");
        f2.setUploadedChunks(1);
        when(fileRepo.findBySessionIdOrderByCreatedAtAsc("S1")).thenReturn(List.of(f1, f2));

        when(chunkRepo.findByFileIdOrderByChunkIndexAsc("F1")).thenReturn(List.of(
                UploadChunk.builder().file(f1).chunkIndex(0).status(ChunkStatus.UPLOADED).build(),
                UploadChunk.builder().file(f1).chunkIndex(1).status(ChunkStatus.PENDING).build(),
                UploadChunk.builder().file(f1).chunkIndex(2).status(ChunkStatus.PENDING).build()
        ));
        when(chunkRepo.findByFileIdOrderByChunkIndexAsc("F2")).thenReturn(List.of(
                UploadChunk.builder().file(f2).chunkIndex(0).status(ChunkStatus.UPLOADED).build()
        ));

        SessionStatusResponse resp = service.getSessionStatus("S1");

        assertThat(resp.getSessionId()).isEqualTo("S1");
        assertThat(resp.getStatus()).isEqualTo(SessionStatus.PAUSED);
        assertThat(resp.getFiles()).hasSize(2);
        assertThat(resp.getFiles().get(0).getPendingChunkIndexes()).containsExactly(1, 2);
        assertThat(resp.getFiles().get(1).getPendingChunkIndexes()).isEmpty();
    }

    @Test
    void completeSession_fails_ifAnyFileNotUploaded() {
        UploadSession sess = newSession("S1", "U", SessionStatus.IN_PROGRESS);
        when(sessionRepo.findById("S1")).thenReturn(Optional.of(sess));
        UploadFile f = newFile("F1", sess, "a.txt", 2, FileStatus.IN_PROGRESS, "S1/F1/a.txt", "U1");
        when(fileRepo.findBySessionIdOrderByCreatedAtAsc("S1")).thenReturn(List.of(f));

        assertThatThrownBy(() -> service.completeSession("S1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not all files are uploaded");
        assertThat(sess.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void completeSession_succeeds_whenAllFilesUploaded() {
        UploadSession sess = newSession("S1", "U", SessionStatus.IN_PROGRESS);
        when(sessionRepo.findById("S1")).thenReturn(Optional.of(sess));
        UploadFile f = newFile("F1", sess, "a.txt", 1, FileStatus.UPLOADED, "S1/F1/a.txt", "U1");
        when(fileRepo.findBySessionIdOrderByCreatedAtAsc("S1")).thenReturn(List.of(f));

        service.completeSession("S1");

        assertThat(sess.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(sessionRepo).save(sess);
    }

    @Test
    void registerFile_throws_ifSessionNotFound() {
        when(sessionRepo.findById("NOPE")).thenReturn(Optional.empty());
        RegisterFileRequest req = new RegisterFileRequest("x.txt", 1L, 1);
        assertThatThrownBy(() -> service.registerFile("NOPE", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
