package com.team6.minidiscord.auth;

import com.team6.minidiscord.auth.dto.AuthResponse;
import com.team6.minidiscord.auth.dto.ForgotPasswordRequest;
import com.team6.minidiscord.auth.dto.LoginRequest;
import com.team6.minidiscord.auth.dto.RegisterRequest;
import com.team6.minidiscord.auth.dto.ResetPasswordRequest;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.common.util.Keys;
import com.team6.minidiscord.security.JwtService;
import com.team6.minidiscord.user.AccountStatus;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserMapper;
import com.team6.minidiscord.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetEmailService passwordResetEmailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final long refreshDays;
    private final long passwordResetMinutes;
    private final String frontendBaseUrl;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetEmailService passwordResetEmailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenHasher tokenHasher,
            @Value("${app.refresh-token.days}") long refreshDays,
            @Value("${app.password-reset.minutes}") long passwordResetMinutes,
            @Value("${app.password-reset.frontend-base-url}") String frontendBaseUrl
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetEmailService = passwordResetEmailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHasher = tokenHasher;
        this.refreshDays = refreshDays;
        this.passwordResetMinutes = passwordResetMinutes;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String emailKey = Keys.normalize(request.email());
        String usernameKey = Keys.normalize(request.username());
        if (userRepository.existsByEmailKey(emailKey)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Email đã được sử dụng.");
        }
        if (userRepository.existsByUsernameKey(usernameKey)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Username đã được sử dụng.");
        }
        Instant now = Instant.now();
        UserDocument user = new UserDocument();
        user.username = request.username().trim();
        user.usernameKey = usernameKey;
        user.email = request.email().trim();
        user.emailKey = emailKey;
        user.passwordHash = passwordEncoder.encode(request.password());
        user.displayName = user.username;
        user.accountStatus = AccountStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        userRepository.save(user);
    }

    @Transactional
    public IssuedAuth login(LoginRequest request, HttpServletRequest httpRequest) {
        UserDocument user = userRepository.findByEmailKey(Keys.normalize(request.email()))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Tài khoản chưa tồn tại."));

        if (user.accountStatus != AccountStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.passwordHash)) {
            throw loginFailed();
        }

        return issue(user, httpRequest, Optional.empty());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        userRepository.findByEmailKey(Keys.normalize(request.email()))
                .filter(user -> user.accountStatus == AccountStatus.ACTIVE)
                .ifPresent(user -> {
                    String resetToken = tokenHasher.newRefreshToken();
                    Instant now = Instant.now();
                    PasswordResetTokenDocument tokenDocument = new PasswordResetTokenDocument();
                    tokenDocument.userId = user.id;
                    tokenDocument.tokenHash = tokenHasher.hash(resetToken);
                    tokenDocument.ipAddress = httpRequest.getRemoteAddr();
                    tokenDocument.createdAt = now;
                    tokenDocument.expiresAt = now.plusSeconds(passwordResetMinutes * 60);
                    passwordResetTokenRepository.save(tokenDocument);

                    String resetLink = frontendBaseUrl.replaceAll("/+$", "") + "/reset-password?token=" + resetToken;
                    passwordResetEmailService.sendResetLink(user, resetLink, passwordResetMinutes);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetTokenDocument resetToken = passwordResetTokenRepository.findByTokenHash(tokenHasher.hash(request.token()))
                .orElseThrow(this::invalidResetToken);
        Instant now = Instant.now();
        if (resetToken.usedAt != null || resetToken.expiresAt.isBefore(now)) {
            throw invalidResetToken();
        }

        UserDocument user = userRepository.findById(resetToken.userId)
                .orElseThrow(this::invalidResetToken);
        if (user.accountStatus != AccountStatus.ACTIVE) {
            throw invalidResetToken();
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword());
        user.updatedAt = now;
        userRepository.save(user);

        resetToken.usedAt = now;
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.id).forEach(token -> {
            token.revokedAt = now;
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(tokenHasher.hash(refreshToken)).ifPresent(token -> {
            if (token.revokedAt == null) {
                token.revokedAt = Instant.now();
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public IssuedAuth refresh(String refreshToken, HttpServletRequest httpRequest) {
        RefreshTokenDocument current = refreshTokenRepository.findByTokenHash(tokenHasher.hash(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ."));
        Instant now = Instant.now();
        if (current.revokedAt != null || current.expiresAt.isBefore(now)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ.");
        }
        UserDocument user = userRepository.findById(current.userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ."));
        if (user.accountStatus != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ.");
        }
        IssuedAuth issued = issue(user, httpRequest, Optional.of(current));
        current.revokedAt = now;
        current.replacedByTokenId = issued.refreshTokenDocument().id;
        refreshTokenRepository.save(current);
        return issued;
    }

    private IssuedAuth issue(UserDocument user, HttpServletRequest httpRequest, Optional<RefreshTokenDocument> replacing) {
        String refreshToken = tokenHasher.newRefreshToken();
        RefreshTokenDocument tokenDocument = new RefreshTokenDocument();
        tokenDocument.userId = user.id;
        tokenDocument.tokenHash = tokenHasher.hash(refreshToken);
        tokenDocument.deviceInfo = httpRequest.getHeader("User-Agent");
        tokenDocument.ipAddress = httpRequest.getRemoteAddr();
        tokenDocument.expiresAt = Instant.now().plusSeconds(refreshDays * 24 * 60 * 60);
        tokenDocument.createdAt = Instant.now();
        tokenDocument = refreshTokenRepository.save(tokenDocument);

        String accessToken = jwtService.createAccessToken(user.id, user.username);
        AuthResponse response = new AuthResponse(accessToken, UserMapper.current(user));
        return new IssuedAuth(response, refreshToken, tokenDocument);
    }

    private ApiException loginFailed() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, "Email hoặc password không đúng.");
    }

    private ApiException invalidResetToken() {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
    }

    public record IssuedAuth(AuthResponse response, String refreshToken, RefreshTokenDocument refreshTokenDocument) {
    }
}
