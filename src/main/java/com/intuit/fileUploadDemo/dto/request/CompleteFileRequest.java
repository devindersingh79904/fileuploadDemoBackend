package com.intuit.fileUploadDemo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteFileRequest {

    @NotBlank
    private String uploadId;

    @NotEmpty @Valid
    private List<PartETag> parts;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartETag {

        @Min(1)
        private int partNumber;

        @NotBlank
        @JsonProperty("eTag")
        private String eTag;
    }
}
