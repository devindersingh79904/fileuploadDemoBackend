package com.intuit.fileUploadDemo.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StartSessionResponse {
    private final String sessionId;
}
