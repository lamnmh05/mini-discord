package com.team6.minidiscord.file;

import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.file.dto.FileUploadResponse;
import com.team6.minidiscord.security.SecurityUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ApiResponse<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose
    ) {
        return ApiResponse.ok(fileStorageService.upload(SecurityUtils.currentUserId(), file, purpose));
    }
}
