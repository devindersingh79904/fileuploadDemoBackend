package com.intuit.fileUploadDemo.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterFileRequest {

    @NotBlank
    private String fileName;

    @Positive
    private long fileSize;

    @Min(1)
    private int chunkCount;
}
