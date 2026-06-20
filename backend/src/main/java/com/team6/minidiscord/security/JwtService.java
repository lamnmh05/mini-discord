package com.team6.minidiscord.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final String issuer;
    private final byte[] secret;
    private final long accessTokenMinutes;

    public JwtService(
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.issuer = issuer;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String createAccessToken(ObjectId userId, String username) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(userId.toHexString())
                    .claim("username", username)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(accessTokenMinutes * 60)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể tạo access token.");
        }
    }

    public AuthenticatedUser verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access token không hợp lệ.");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access token không hợp lệ.");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Access token đã hết hạn.");
            }
            String subject = claims.getSubject();
            if (!ObjectId.isValid(subject)) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access token không hợp lệ.");
            }
            return new AuthenticatedUser(new ObjectId(subject), (String) claims.getClaim("username"));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Access token không hợp lệ.");
        }
    }
}
