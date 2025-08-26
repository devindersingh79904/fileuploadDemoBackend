package com.intuit.fileUploadDemo.dto.response;

import com.intuit.fileUploadDemo.entities.enums.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SessionStatusResponse {
    private final String sessionId;
    private final List<FileStatusItem> files;

    @Getter
    @AllArgsConstructor
    public static class FileStatusItem{
        private final String fileId;
        private final String fileName;
        private final int totalChunks;
        private final int uploadedChunks;
        private final FileStatus status;
        private final List<Integer> pendingChunkIndexes;
    }
}
