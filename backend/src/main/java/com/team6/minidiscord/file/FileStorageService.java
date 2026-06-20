package com.team6.minidiscord.file;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.file.dto.FileUploadResponse;
import com.team6.minidiscord.message.Attachment;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final String bucket;
    private final String publicBaseUrl;
    private final Set<String> allowedMimeTypes;
    private final Duration uploadTtl;

    public FileStorageService(
            MinioClient minioClient,
            StringRedisTemplate redisTemplate,
            @Value("${app.file.bucket}") String bucket,
            @Value("${app.file.public-base-url}") String publicBaseUrl,
            @Value("${app.file.allowed-mime-types}") String allowedMimeTypes,
            @Value("${app.file.upload-ttl-minutes}") long uploadTtlMinutes
    ) {
        this.minioClient = minioClient;
        this.redisTemplate = redisTemplate;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
        this.allowedMimeTypes = Arrays.stream(allowedMimeTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.uploadTtl = Duration.ofMinutes(uploadTtlMinutes);
    }

    public FileUploadResponse upload(ObjectId userId, MultipartFile file, String purpose) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File rỗng.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File vượt quá 10 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedMimeTypes.contains(contentType)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "MIME type không được hỗ trợ.");
        }
        try {
            byte[] bytes = file.getBytes();
            validateMagicBytes(contentType, bytes);
            String originalName = safeName(file.getOriginalFilename());
            String storageKey = "uploads/%s/%s/%s-%s".formatted(
                    userId.toHexString(),
                    purpose == null || purpose.isBlank() ? "message" : purpose,
                    Instant.now().toEpochMilli(),
                    UUID.randomUUID()
            );
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
            String fileUrl = publicBaseUrl.replaceAll("/+$", "") + "/" + bucket + "/" + URLEncoder.encode(storageKey, StandardCharsets.UTF_8);
            redisTemplate.opsForValue().set(uploadKey(userId, storageKey), "1", uploadTtl);
            return new FileUploadResponse(storageKey, fileUrl, originalName, contentType, file.getSize());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Upload file thất bại.");
        }
    }

    public void verifyOwnership(ObjectId userId, List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > 10) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mỗi message tối đa 10 attachment.");
        }
        for (Attachment attachment : attachments) {
            if (attachment.fileSize > MAX_FILE_SIZE) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Attachment vượt quá 10 MB.");
            }
            Boolean exists = redisTemplate.hasKey(uploadKey(userId, attachment.storageKey));
            if (!Boolean.TRUE.equals(exists)) {
                throw new ApiException(ErrorCode.RESOURCE_FORBIDDEN, "Attachment không thuộc upload của user hiện tại.");
            }
        }
    }

    public void consumeOwnership(ObjectId userId, List<Attachment> attachments) {
        if (attachments == null) {
            return;
        }
        attachments.forEach(attachment -> redisTemplate.delete(uploadKey(userId, attachment.storageKey)));
    }

    private String uploadKey(ObjectId userId, String storageKey) {
        return "upload:%s:%s".formatted(userId.toHexString(), storageKey);
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        return name.replaceAll("[\\\\/\\r\\n\\t]", "_");
    }

    private void validateMagicBytes(String contentType, byte[] bytes) {
        if ("image/png".equals(contentType) && (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File PNG không hợp lệ.");
        }
        if ("image/jpeg".equals(contentType) && (bytes.length < 3 || bytes[0] != (byte) 0xFF || bytes[1] != (byte) 0xD8)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File JPEG không hợp lệ.");
        }
        if ("application/pdf".equals(contentType) && (bytes.length < 4 || bytes[0] != 0x25 || bytes[1] != 0x50 || bytes[2] != 0x44 || bytes[3] != 0x46)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File PDF không hợp lệ.");
        }
    }
}
