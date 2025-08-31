package com.intuit.fileUploadDemo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilePartsResponse {
    private String fileId;
    private String s3Key;
    private String uploadId;

    private int totalChunks;
    private List<Integer> uploadedPartNumbers;  // e.g. [1,2,5]
    private List<Integer> pendingPartNumbers;   // e.g. [3,4,6,7,8,9,10]

    // Optional: if you also want the ETags the frontend could reuse at complete time.
    private List<UploadedPart> uploadedParts;   // each has partNumber + eTag

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadedPart {
        private int partNumber;
        private String eTag; // as returned by S3 ListParts
    }
}
