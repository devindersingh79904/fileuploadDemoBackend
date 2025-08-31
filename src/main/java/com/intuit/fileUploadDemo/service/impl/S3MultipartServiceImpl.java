package com.intuit.fileUploadDemo.service.impl;

import com.intuit.fileUploadDemo.service.S3MultipartService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3MultipartServiceImpl implements S3MultipartService {

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Override
    public String start(String key, String contentType) {
        CreateMultipartUploadResponse resp = s3.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType((contentType == null || contentType.isBlank())
                                ? "application/octet-stream"
                                : contentType)
                        .build()
        );
        return resp.uploadId();
    }

    @Override
    public String presignPart(String key, String uploadId, int partNumber, long contentLength) {
        UploadPartRequest upr = UploadPartRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .contentLength(contentLength > 0 ? contentLength : null)
                .build();

        PresignedUploadPartRequest presigned = presigner.presignUploadPart(
                UploadPartPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .uploadPartRequest(upr)
                        .build()
        );

        return presigned.url().toString();
    }

    @Override
    public void complete(String key, String uploadId, List<Map.Entry<Integer, String>> parts) {
        CompletedMultipartUpload completed = CompletedMultipartUpload.builder()
                .parts(parts.stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .map(e -> CompletedPart.builder()
                                .partNumber(e.getKey())
                                .eTag(e.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(completed)
                .build());
    }

    @Override
    public void abort(String key, String uploadId) {
        s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .build());
    }


    @Override
    public List<Map.Entry<Integer, String>> listParts(String key, String uploadId) {
        List<Map.Entry<Integer, String>> parts = new ArrayList<>();
        Integer partMarker = null;

        boolean isTruncated;
        do {
            ListPartsResponse resp = s3.listParts(
                    ListPartsRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .uploadId(uploadId)
                            .partNumberMarker(partMarker)
                            .build()
            );

            resp.parts().forEach(p ->
                    parts.add(Map.entry(p.partNumber(), p.eTag()))
            );

            isTruncated = resp.isTruncated();
            partMarker = resp.nextPartNumberMarker();
        } while (isTruncated);

        return parts.stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .toList();
    }
}
