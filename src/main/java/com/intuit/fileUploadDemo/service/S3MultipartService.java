package com.intuit.fileUploadDemo.service;

import java.util.List;
import java.util.Map;

public interface S3MultipartService {

    /**
     * Create a multipart upload in S3 for the given key.
     * @param key S3 object key (e.g., sessionId/fileId/filename)
     * @param contentType nullable; defaults to application/octet-stream if null/blank
     * @return uploadId from S3
     */
    String start(String key, String contentType);

    /**
     * Generate a presigned UploadPart URL for the given part number.
     * @param key S3 object key
     * @param uploadId S3 multipart uploadId
     * @param partNumber 1-based part number
     * @param contentLength optional; pass 0 if unknown (signature won’t bind it)
     * @return presigned URL (HTTP PUT)
     */
    String presignPart(String key, String uploadId, int partNumber, long contentLength);

    /**
     * Complete the multipart upload with the provided parts (partNumber -> ETag).
     * @param key S3 object key
     * @param uploadId S3 multipart uploadId
     * @param parts ordered or unordered list of (partNumber, eTag); service will sort
     */
    void complete(String key, String uploadId, List<Map.Entry<Integer, String>> parts);

    /**
     * Abort a multipart upload.
     */
    void abort(String key, String uploadId);

    /**
     * List already-uploaded parts for an in-progress multipart upload.
     * Returns (partNumber, eTag) entries sorted by partNumber.
     */
    List<Map.Entry<Integer, String>> listParts(String key, String uploadId);
}
