package com.intuit.fileUploadDemo.service;


import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.PresignPartUrlResponse;
import com.intuit.fileUploadDemo.dto.response.RegisterFileResponse;
import com.intuit.fileUploadDemo.dto.response.SessionStatusResponse;
import com.intuit.fileUploadDemo.dto.response.StartSessionResponse;

public interface UploadService {

    StartSessionResponse startSession(StartSessionRequest request);
    RegisterFileResponse registerFile(String sessionId, RegisterFileRequest request);
    PresignPartUrlResponse presignPartUrl(String fileId, PresignPartUrlRequest request);
    void completeFile(String fileId, CompleteFileRequest request);
    SessionStatusResponse getSessionStatus(String sessionId);


    void pauseSession(String sessionId);
    void resumeSession(String sessionId);

    void pauseFile(String fileId);
    void resumeFile(String fileId);

    void completeSession(String sessionId);
}
