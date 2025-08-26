package com.intuit.fileUploadDemo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterFileResponse {

    private final String fileId;
    private final String s3Key;
    private final String uploadId;
}
