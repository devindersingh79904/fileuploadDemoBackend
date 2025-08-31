package com.intuit.fileUploadDemo.service;


import com.intuit.fileUploadDemo.dto.request.CompleteFileRequest;
import com.intuit.fileUploadDemo.dto.request.PresignPartUrlRequest;
import com.intuit.fileUploadDemo.dto.request.RegisterFileRequest;
import com.intuit.fileUploadDemo.dto.request.StartSessionRequest;
import com.intuit.fileUploadDemo.dto.response.*;

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
    FilePartsResponse getFileParts(String fileId);
}
