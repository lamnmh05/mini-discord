package com.team6.minidiscord.auth;

import com.team6.minidiscord.auth.dto.LoginRequest;
import com.team6.minidiscord.auth.dto.RegisterRequest;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.security.JwtService;
import com.team6.minidiscord.user.AccountStatus;
import com.team6.minidiscord.user.UserDocument;
import com.team6.minidiscord.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private HttpServletRequest request;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                tokenHasher,
                30
        );
    }

    @Test
    void registerNormalizesKeysAndStoresActiveUser() {
        when(userRepository.existsByEmailKey("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameKey("alice_01")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest(" Alice_01 ", " Alice@Example.COM ", "Password123!"));

        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userRepository).save(captor.capture());
        UserDocument saved = captor.getValue();

        assertThat(saved.username).isEqualTo("Alice_01");
        assertThat(saved.usernameKey).isEqualTo("alice_01");
        assertThat(saved.email).isEqualTo("Alice@Example.COM");
        assertThat(saved.emailKey).isEqualTo("alice@example.com");
        assertThat(saved.passwordHash).isEqualTo("encoded-password");
        assertThat(saved.displayName).isEqualTo("Alice_01");
        assertThat(saved.accountStatus).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.createdAt).isNotNull();
        assertThat(saved.updatedAt).isNotNull();
    }

    @Test
    void registerRejectsDuplicateEmailBeforeSaving() {
        when(userRepository.existsByEmailKey("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "alice@example.com", "Password123!")))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginIssuesAccessTokenAndRefreshTokenDocument() {
        ObjectId userId = new ObjectId();
        UserDocument user = new UserDocument();
        user.id = userId;
        user.username = "alice";
        user.email = "alice@example.com";
        user.passwordHash = "encoded-password";
        user.accountStatus = AccountStatus.ACTIVE;

        when(userRepository.findByEmailKey("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "encoded-password")).thenReturn(true);
        when(tokenHasher.newRefreshToken()).thenReturn("refresh-token");
        when(tokenHasher.hash("refresh-token")).thenReturn("refresh-hash");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(refreshTokenRepository.save(any(RefreshTokenDocument.class))).thenAnswer(invocation -> {
            RefreshTokenDocument token = invocation.getArgument(0);
            token.id = new ObjectId();
            return token;
        });
        when(jwtService.createAccessToken(userId, "alice")).thenReturn("access-token");

        AuthService.IssuedAuth issued = authService.login(
                new LoginRequest(" Alice@Example.COM ", "Password123!"),
                request
        );

        assertThat(issued.response().accessToken()).isEqualTo("access-token");
        assertThat(issued.response().user().id()).isEqualTo(userId.toHexString());
        assertThat(issued.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<RefreshTokenDocument> captor = ArgumentCaptor.forClass(RefreshTokenDocument.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenDocument savedToken = captor.getValue();

        assertThat(savedToken.userId).isEqualTo(userId);
        assertThat(savedToken.tokenHash).isEqualTo("refresh-hash");
        assertThat(savedToken.deviceInfo).isEqualTo("JUnit");
        assertThat(savedToken.ipAddress).isEqualTo("127.0.0.1");
        assertThat(savedToken.expiresAt).isAfter(savedToken.createdAt);
    }

    @Test
    void loginRejectsWrongPassword() {
        UserDocument user = new UserDocument();
        user.passwordHash = "encoded-password";
        user.accountStatus = AccountStatus.ACTIVE;

        when(userRepository.findByEmailKey("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password"), request))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        verify(refreshTokenRepository, never()).save(any());
    }
}
