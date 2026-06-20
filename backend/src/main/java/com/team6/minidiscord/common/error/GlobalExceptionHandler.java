package com.team6.minidiscord.common.error;

import com.team6.minidiscord.common.api.ApiError;
import com.team6.minidiscord.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApi(ApiException ex) {
        return ResponseEntity
                .status(ex.code().status())
                .body(ApiResponse.fail(new ApiError(ex.code().name(), ex.getMessage(), ex.details(), traceId())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return validation(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return validation(details);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(DuplicateKeyException ex) {
        return ResponseEntity.status(ErrorCode.DUPLICATE_RESOURCE.status())
                .body(ApiResponse.fail(new ApiError(
                        ErrorCode.DUPLICATE_RESOURCE.name(),
                        "Dữ liệu đã tồn tại.",
                        List.of(),
                        traceId()
                )));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.fail(new ApiError(
                        ErrorCode.INTERNAL_ERROR.name(),
                        "Lỗi hệ thống.",
                        List.of(),
                        traceId()
                )));
    }

    private ResponseEntity<ApiResponse<Object>> validation(List<String> details) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail(new ApiError(
                        ErrorCode.VALIDATION_ERROR.name(),
                        "Dữ liệu không hợp lệ.",
                        details,
                        traceId()
                )));
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}
