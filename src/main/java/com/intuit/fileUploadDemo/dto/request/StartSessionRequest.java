package com.intuit.fileUploadDemo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartSessionRequest {

    @NotBlank
    private String userId;
}
