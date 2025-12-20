package com.somtranscriber.common.security;

import com.somtranscriber.auth.model.UserEntity;
import com.somtranscriber.auth.model.UserRole;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final AppProperties properties;
    private SecretKey secretKey;

    public JwtService(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initKey() {
        byte[] source = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (source.length < 32) {
            byte[] expanded = new byte[32];
            for (int i = 0; i < expanded.length; i++) {
                expanded[i] = source[i % source.length];
            }
            source = expanded;
        }
        this.secretKey = Keys.hmacShaKeyFor(source);
    }

    public TokenData generateAccessToken(UserEntity user) {
        Instant expiresAt = Instant.now().plus(properties.jwt().accessTtlMinutes(), ChronoUnit.MINUTES);
        String token = buildToken(user, expiresAt, "access", UUID.randomUUID().toString());
        return new TokenData(token, expiresAt, null);
    }

    public TokenData generateRefreshToken(UserEntity user) {
        Instant expiresAt = Instant.now().plus(properties.jwt().refreshTtlDays(), ChronoUnit.DAYS);
        String tokenId = UUID.randomUUID().toString();
        String token = buildToken(user, expiresAt, "refresh", tokenId);
        return new TokenData(token, expiresAt, tokenId);
    }

    private String buildToken(UserEntity user, Instant expiresAt, String type, String tokenId) {
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .claims(Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "type", type,
                        "jti", tokenId
                ))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public ParsedToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            String roleValue = claims.get("role", String.class);
            String tokenType = claims.get("type", String.class);
            String tokenId = claims.get("jti", String.class);
            Date expiration = claims.getExpiration();

            if (subject == null || tokenType == null || tokenId == null || expiration == null) {
                throw new UnauthorizedException("Invalid token payload");
            }

            return new ParsedToken(
                    UUID.fromString(subject),
                    email,
                    UserRole.valueOf(roleValue),
                    tokenType,
                    tokenId,
                    expiration.toInstant()
            );
        } catch (IllegalArgumentException | JwtException exception) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    public record TokenData(String token, Instant expiresAt, String tokenId) {
    }

    public record ParsedToken(
            UUID userId,
            String email,
            UserRole role,
            String tokenType,
            String tokenId,
            Instant expiresAt
    ) {
    }
}
