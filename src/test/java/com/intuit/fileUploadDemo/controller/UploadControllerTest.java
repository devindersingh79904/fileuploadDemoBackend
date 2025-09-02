package com.intuit.fileUploadDemo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.*;
import com.intuit.fileUploadDemo.exception.GlobalExceptionHandler;
import com.intuit.fileUploadDemo.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UploadController.class)
@Import(GlobalExceptionHandler.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UploadService uploadService;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {}

    @Test
    void startSession_ok() throws Exception {
        Mockito.when(uploadService.startSession(any(StartSessionRequest.class)))
                .thenReturn(new StartSessionResponse("S123"));

        StartSessionRequest req = new StartSessionRequest();
        req.setUserId("user-1");

        mvc.perform(post("/api/v1/upload/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("S123"));
    }
    // 400: startSession missing userId
    @Test
    void startSession_badRequest_when_userId_missing() throws Exception {
        // empty body -> violates @Valid on StartSessionRequest.userId
        mvc.perform(post("/api/v1/upload/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // 404: session status for unknown session
    @Test
    void sessionStatus_404_when_session_not_found() throws Exception {
        Mockito.when(uploadService.getSessionStatus("NO"))
                .thenThrow(new com.intuit.fileUploadDemo.exception.ResourceNotFoundException("Session not found"));

        mvc.perform(get("/api/v1/upload/{sessionId}/status", "NO"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerFile_ok() throws Exception {
        Mockito.when(uploadService.registerFile(eq("S123"), any(RegisterFileRequest.class)))
                .thenReturn(new RegisterFileResponse("F1", "S123/F1/report.pdf", "upl-1"));

        RegisterFileRequest req = new RegisterFileRequest();
        req.setFileName("report.pdf");
        req.setFileSize(12345L);
        req.setChunkCount(3);

        mvc.perform(post("/api/v1/upload/{sessionId}/files", "S123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("F1"))
                .andExpect(jsonPath("$.s3Key").value("S123/F1/report.pdf"))
                .andExpect(jsonPath("$.uploadId").value("upl-1"));
    }

    // 400: registerFile invalid chunkCount (<=0)
    @Test
    void registerFile_badRequest_when_chunkCount_invalid() throws Exception {
        RegisterFileRequest req = new RegisterFileRequest();
        req.setFileName("report.pdf");
        req.setFileSize(12345L);
        req.setChunkCount(0); // invalid

        mvc.perform(post("/api/v1/upload/{sessionId}/files", "S123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }


    @Test
    void sessionStatus_ok() throws Exception {
        SessionStatusResponse.FileStatusItem item =
                new SessionStatusResponse.FileStatusItem("F1","report.pdf",3,1,
                        com.intuit.fileUploadDemo.entities.enums.FileStatus.IN_PROGRESS, List.of(2,3));
        Mockito.when(uploadService.getSessionStatus("S123"))
                .thenReturn(new SessionStatusResponse("S123",
                        com.intuit.fileUploadDemo.entities.enums.SessionStatus.IN_PROGRESS,
                        List.of(item)));

        mvc.perform(get("/api/v1/upload/{sessionId}/status","S123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("S123"))
                .andExpect(jsonPath("$.files[0].fileId").value("F1"));
    }

    @Test
    void presignPart_ok() throws Exception {
        Mockito.when(uploadService.presignPartUrl(eq("F1"), any(PresignPartUrlRequest.class)))
                .thenReturn(new PresignPartUrlResponse("https://s3/presigned"));

        PresignPartUrlRequest req = new PresignPartUrlRequest();
        req.setPartNumber(1);

        mvc.perform(post("/api/v1/upload/files/{fileId}/parts/url","F1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void completeFile_ok() throws Exception {
        Mockito.doNothing().when(uploadService).completeFile(eq("F1"), any(CompleteFileRequest.class));

        CompleteFileRequest req = new CompleteFileRequest();
        req.setUploadId("upl-1");
        CompleteFileRequest.PartETag p1 = new CompleteFileRequest.PartETag();
        p1.setPartNumber(1);
        p1.setETag("etag-1");
        req.setParts(List.of(p1));

        mvc.perform(patch("/api/v1/upload/files/{fileId}/complete","F1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    void pause_resume_complete_session_ok() throws Exception {
        Mockito.doNothing().when(uploadService).pauseSession("S123");
        Mockito.doNothing().when(uploadService).resumeSession("S123");
        Mockito.doNothing().when(uploadService).completeSession("S123");

        mvc.perform(patch("/api/v1/upload/{sessionId}/pause","S123"))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/v1/upload/{sessionId}/resume","S123"))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/v1/upload/{sessionId}/complete","S123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void pause_resume_file_ok() throws Exception {
        Mockito.doNothing().when(uploadService).pauseFile("F1");
        Mockito.doNothing().when(uploadService).resumeFile("F1");

        mvc.perform(patch("/api/v1/upload/files/{fileId}/pause","F1"))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/v1/upload/files/{fileId}/resume","F1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getFileParts_ok() throws Exception {
        FilePartsResponse.UploadedPart up = new FilePartsResponse.UploadedPart(1,"etag-1");
        FilePartsResponse resp = new FilePartsResponse();
        resp.setFileId("F1");
        resp.setS3Key("S123/F1/report.pdf");
        resp.setUploadId("upl-1");
        resp.setUploadedParts(List.of(up));
        resp.setPendingPartNumbers(List.of(2,3));

        Mockito.when(uploadService.getFileParts("F1")).thenReturn(resp);

        mvc.perform(get("/api/v1/upload/files/{fileId}/parts","F1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("F1"))
                .andExpect(jsonPath("$.uploadedParts[0].partNumber").value(1));
    }

    // 404: file parts for unknown file
    @Test
    void getFileParts_404_when_file_not_found() throws Exception {
        Mockito.when(uploadService.getFileParts("NO"))
                .thenThrow(new com.intuit.fileUploadDemo.exception.ResourceNotFoundException("File not found"));

        mvc.perform(get("/api/v1/upload/files/{fileId}/parts", "NO"))
                .andExpect(status().isNotFound());
    }

}
