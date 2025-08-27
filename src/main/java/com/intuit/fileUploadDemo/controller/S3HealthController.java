package com.intuit.fileUploadDemo.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/s3")
public class S3HealthController {

    private final S3Client s3;
    @Value("${app.s3.bucket}")
    private String bucket;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        return ResponseEntity.ok("OK: bucket=" + bucket);
    }
}
