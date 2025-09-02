package com.intuit.fileUploadDemo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.RegisterFileResponse;
import com.intuit.fileUploadDemo.dto.response.SessionStatusResponse;
import com.intuit.fileUploadDemo.dto.response.StartSessionResponse;
import com.intuit.fileUploadDemo.exception.GlobalExceptionHandler;
import com.intuit.fileUploadDemo.exception.ResourceNotFoundException;
import com.intuit.fileUploadDemo.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for UploadController
 */
@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UploadService uploadService;

    @InjectMocks
    private UploadController uploadController;

    private ObjectMapper objectMapper;

    private static final String SESSION_ID = "test-session-123";
    private static final String FILE_ID = "test-file-456";
    private static final String USER_ID = "test-user-789";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(uploadController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should successfully start a new session")
    void startSession_Success() throws Exception {
        StartSessionRequest request = new StartSessionRequest(USER_ID);
        StartSessionResponse expectedResponse = new StartSessionResponse(SESSION_ID);

        when(uploadService.startSession(any(StartSessionRequest.class)))
                .thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/upload/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID));

        verify(uploadService, times(1)).startSession(any(StartSessionRequest.class));
    }

    @Test
    @DisplayName("Should return 400 when starting session with invalid request")
    void startSession_InvalidRequest() throws Exception {
        StartSessionRequest invalidRequest = new StartSessionRequest("");

        mockMvc.perform(post("/api/v1/upload/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(uploadService, never()).startSession(any());
    }

    @Test
    @DisplayName("Should successfully register a file under a session")
    void registerFile_Success() throws Exception {
        RegisterFileRequest request = new RegisterFileRequest("test-file.txt", 1024L, 5);
        RegisterFileResponse expectedResponse = new RegisterFileResponse(FILE_ID, "s3-key", "upload-id");

        when(uploadService.registerFile(eq(SESSION_ID), any(RegisterFileRequest.class)))
                .thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/upload/{sessionId}/files", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(FILE_ID))
                .andExpect(jsonPath("$.s3Key").value("s3-key"))
                .andExpect(jsonPath("$.uploadId").value("upload-id"));

        verify(uploadService, times(1)).registerFile(eq(SESSION_ID), any(RegisterFileRequest.class));
    }

    @Test
    @DisplayName("Should successfully get session status")
    void getSessionStatus_Success() throws Exception {
        SessionStatusResponse.FileStatusItem fileStatus = new SessionStatusResponse.FileStatusItem(
                FILE_ID, "test.txt", 5, 5, 
                com.intuit.fileUploadDemo.entities.enums.FileStatus.UPLOADED, Arrays.asList()
        );
        SessionStatusResponse expectedResponse = new SessionStatusResponse(
                SESSION_ID, 
                com.intuit.fileUploadDemo.entities.enums.SessionStatus.IN_PROGRESS, 
                Arrays.asList(fileStatus)
        );

        when(uploadService.getSessionStatus(SESSION_ID)).thenReturn(expectedResponse);

        mockMvc.perform(get("/api/v1/upload/{sessionId}/status", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files.length()").value(1));

        verify(uploadService, times(1)).getSessionStatus(SESSION_ID);
    }

    @Test
    @DisplayName("Should successfully pause a session")
    void pauseSession_Success() throws Exception {
        doNothing().when(uploadService).pauseSession(SESSION_ID);

        mockMvc.perform(patch("/api/v1/upload/{sessionId}/pause", SESSION_ID))
                .andExpect(status().isNoContent());

        verify(uploadService, times(1)).pauseSession(SESSION_ID);
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException with 404 status")
    void handleResourceNotFoundException() throws Exception {
        when(uploadService.getSessionStatus(SESSION_ID))
                .thenThrow(new ResourceNotFoundException("Session not found: " + SESSION_ID));

        mockMvc.perform(get("/api/v1/upload/{sessionId}/status", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Session not found: " + SESSION_ID));

        verify(uploadService, times(1)).getSessionStatus(SESSION_ID);
    }

    @Test
    @DisplayName("Should handle service exceptions gracefully")
    void handleServiceException() throws Exception {
        when(uploadService.startSession(any(StartSessionRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        StartSessionRequest request = new StartSessionRequest(USER_ID);

        mockMvc.perform(post("/api/v1/upload/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Something went wrong"));

        verify(uploadService, times(1)).startSession(any(StartSessionRequest.class));
    }
}
