package com.intuit.fileUploadDemo.service.impl;

import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.PresignPartUrlResponse;
import com.intuit.fileUploadDemo.dto.response.RegisterFileResponse;
import com.intuit.fileUploadDemo.dto.response.StartSessionResponse;
import com.intuit.fileUploadDemo.entities.UploadChunk;
import com.intuit.fileUploadDemo.entities.UploadFile;
import com.intuit.fileUploadDemo.entities.UploadSession;
import com.intuit.fileUploadDemo.entities.enums.FileStatus;
import com.intuit.fileUploadDemo.entities.enums.SessionStatus;
import com.intuit.fileUploadDemo.exception.ResourceNotFoundException;
import com.intuit.fileUploadDemo.repository.UploadChunkRepository;
import com.intuit.fileUploadDemo.repository.UploadFileRepository;
import com.intuit.fileUploadDemo.repository.UploadSessionRepository;
import com.intuit.fileUploadDemo.service.S3MultipartService;
import com.intuit.fileUploadDemo.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock UploadSessionRepository sessionRepo;
    @Mock UploadFileRepository fileRepo;
    @Mock UploadChunkRepository chunkRepo;
    @Mock
    S3MultipartService s3;

    @InjectMocks UploadServiceImpl service;

    @BeforeEach
    void init() {}

    @Test
    void startSession_creates_when_no_existing() {
        when(sessionRepo.findFirstByUserIdAndStatusIn(eq("u1"), any())).thenReturn(Optional.empty());
        when(sessionRepo.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

        StartSessionRequest req = new StartSessionRequest();
        req.setUserId("u1");

        StartSessionResponse resp = service.startSession(req);

        assertNotNull(resp.getSessionId());
        verify(sessionRepo).save(any(UploadSession.class));
    }

    @Test
    void startSession_reuses_existing() {
        UploadSession existing = mock(UploadSession.class);
        when(existing.getId()).thenReturn("S123");
        when(sessionRepo.findFirstByUserIdAndStatusIn(eq("u1"), any())).thenReturn(Optional.of(existing));

        StartSessionRequest req = new StartSessionRequest();
        req.setUserId("u1");
        StartSessionResponse resp = service.startSession(req);

        assertEquals("S123", resp.getSessionId());
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void registerFile_ok() {
        UploadSession sess = mock(UploadSession.class);
        when(sess.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(sessionRepo.findById("S123")).thenReturn(Optional.of(sess));

        when(s3.start(anyString(), anyString())).thenReturn("upl-1");
        when(fileRepo.save(any(UploadFile.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterFileRequest req = new RegisterFileRequest();
        req.setFileName("report.pdf");
        req.setFileSize(1000L);
        req.setChunkCount(3);

        RegisterFileResponse resp = service.registerFile("S123", req);

        assertNotNull(resp.getFileId());
        assertEquals("upl-1", resp.getUploadId());
        assertTrue(resp.getS3Key().endsWith("/report.pdf"));
        verify(chunkRepo, times(3)).save(any(UploadChunk.class));
    }

    @Test
    void registerFile_throws_when_session_missing() {
        when(sessionRepo.findById("NO")).thenReturn(Optional.empty());
        RegisterFileRequest req = new RegisterFileRequest();
        req.setFileName("a");
        req.setFileSize(1);
        req.setChunkCount(1);

        assertThrows(ResourceNotFoundException.class, () -> service.registerFile("NO", req));
    }

    @Test
    void presignPart_ok() {
        UploadFile file = mock(UploadFile.class);
        when(file.getStatus()).thenReturn(FileStatus.IN_PROGRESS);
        when(file.getTotalChunks()).thenReturn(3);
        when(file.getS3Key()).thenReturn("k");
        when(file.getUploadId()).thenReturn("upl-1");
        when(fileRepo.findById("F1")).thenReturn(Optional.of(file));
        when(chunkRepo.findByFileIdAndChunkIndex("F1", 0)).thenReturn(Optional.of(mock(UploadChunk.class)));
        when(s3.presignPart("k","upl-1",1,0L)).thenReturn("https://s3/presigned");

        PresignPartUrlRequest req = new PresignPartUrlRequest();
        req.setPartNumber(1);

        PresignPartUrlResponse resp = service.presignPartUrl("F1", req);

        assertTrue(resp.getUrl().contains("http"));
    }

    @Test
    void completeFile_ok() {
        UploadFile file = mock(UploadFile.class);
        when(file.getUploadId()).thenReturn("upl-1");
        when(file.getTotalChunks()).thenReturn(2);
        when(file.getS3Key()).thenReturn("k");
        when(file.getSession()).thenReturn(mock(UploadSession.class));
        when(fileRepo.findById("F1")).thenReturn(Optional.of(file));

        UploadChunk c0 = mock(UploadChunk.class);
        when(c0.getChunkIndex()).thenReturn(0);
        UploadChunk c1 = mock(UploadChunk.class);
        when(c1.getChunkIndex()).thenReturn(1);
        when(chunkRepo.findByFileIdOrderByChunkIndexAsc("F1")).thenReturn(List.of(c0,c1));

        doNothing().when(s3).complete(eq("k"), eq("upl-1"), anyList());
        when(fileRepo.save(any(UploadFile.class))).thenAnswer(inv -> inv.getArgument(0));

        CompleteFileRequest.PartETag p1 = new CompleteFileRequest.PartETag();
        p1.setPartNumber(1);
        p1.setETag("e1");
        CompleteFileRequest.PartETag p2 = new CompleteFileRequest.PartETag();
        p2.setPartNumber(2);
        p2.setETag("e2");

        CompleteFileRequest req = new CompleteFileRequest();
        req.setUploadId("upl-1");
        req.setParts(List.of(p1,p2));

        assertDoesNotThrow(() -> service.completeFile("F1", req));
        verify(s3).complete(eq("k"), eq("upl-1"), anyList());
        verify(fileRepo).save(any(UploadFile.class));
    }

    @Test
    void completeFile_mismatch_uploadId_throws() {
        UploadFile file = mock(UploadFile.class);
        when(file.getUploadId()).thenReturn("upl-1");
        when(fileRepo.findById("F1")).thenReturn(Optional.of(file));

        CompleteFileRequest req = new CompleteFileRequest();
        req.setUploadId("different");

        assertThrows(IllegalArgumentException.class, () -> service.completeFile("F1", req));
    }

    @Test
    void completeSession_all_uploaded_ok() {
        UploadSession sess = mock(UploadSession.class);
        when(sess.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(sessionRepo.findById("S123")).thenReturn(Optional.of(sess));

        UploadFile f1 = mock(UploadFile.class);
        when(f1.getStatus()).thenReturn(FileStatus.UPLOADED);
        UploadFile f2 = mock(UploadFile.class);
        when(f2.getStatus()).thenReturn(FileStatus.UPLOADED);
        when(fileRepo.findBySessionIdOrderByCreatedAtAsc("S123")).thenReturn(List.of(f1,f2));

        assertDoesNotThrow(() -> service.completeSession("S123"));
        verify(sessionRepo).save(any(UploadSession.class));
    }

    @Test
    void completeSession_blocks_when_any_file_pending() {
        UploadSession sess = mock(UploadSession.class);
        when(sess.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(sessionRepo.findById("S123")).thenReturn(Optional.of(sess));

        UploadFile f1 = mock(UploadFile.class);
        when(f1.getStatus()).thenReturn(FileStatus.UPLOADED);
        UploadFile f2 = mock(UploadFile.class);
        when(f2.getStatus()).thenReturn(FileStatus.IN_PROGRESS);
        when(fileRepo.findBySessionIdOrderByCreatedAtAsc("S123")).thenReturn(List.of(f1,f2));

        assertThrows(IllegalStateException.class, () -> service.completeSession("S123"));
        verify(sessionRepo, never()).save(any());
    }
}
