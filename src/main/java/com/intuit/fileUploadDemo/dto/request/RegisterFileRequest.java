package com.intuit.fileUploadDemo.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFileRequest {

    @NotBlank
    private String fileName;

    @Positive
    private long fileSize;

    @Min(1)
    private int chunkCount;
}
