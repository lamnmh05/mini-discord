package com.team6.minidiscord.auth;

import com.team6.minidiscord.auth.dto.AuthResponse;
import com.team6.minidiscord.auth.dto.LoginRequest;
import com.team6.minidiscord.auth.dto.RegisterRequest;
import com.team6.minidiscord.common.api.ApiResponse;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok(Map.of("message", "Đăng ký thành công."));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthService.IssuedAuth issued = authService.login(request, httpRequest);
        cookieService.setRefreshToken(httpResponse, issued.refreshToken());
        return ApiResponse.ok(issued.response());
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.readRefreshToken(request).ifPresent(authService::logout);
        cookieService.clearRefreshToken(response);
        return ApiResponse.ok(Map.of("message", "Đăng xuất thành công."));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieService.readRefreshToken(request)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Thiếu refresh token."));
        AuthService.IssuedAuth issued = authService.refresh(refreshToken, request);
        cookieService.setRefreshToken(response, issued.refreshToken());
        return ApiResponse.ok(issued.response());
    }
}
