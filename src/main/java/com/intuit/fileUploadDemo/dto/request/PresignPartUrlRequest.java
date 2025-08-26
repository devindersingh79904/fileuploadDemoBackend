package com.intuit.fileUploadDemo.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresignPartUrlRequest {

    @Min(1)
    private int partNumber;
}
