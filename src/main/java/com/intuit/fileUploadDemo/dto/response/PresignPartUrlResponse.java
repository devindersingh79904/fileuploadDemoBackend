package com.intuit.fileUploadDemo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresignPartUrlResponse {
    private final String url;
}
