package com.somtranscriber.auth.service;

import com.somtranscriber.auth.dto.*;
import com.somtranscriber.auth.model.*;
import com.somtranscriber.auth.repo.InviteRepository;
import com.somtranscriber.auth.repo.RefreshTokenRepository;
import com.somtranscriber.auth.repo.UserRepository;
import com.somtranscriber.common.exception.BadRequestException;
import com.somtranscriber.common.exception.UnauthorizedException;
import com.somtranscriber.common.security.AuthenticatedUser;
import com.somtranscriber.common.security.JwtService;
import com.somtranscriber.common.util.Hashing;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       InviteRepository inviteRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.username().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.pin(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        JwtService.ParsedToken parsed = jwtService.parse(request.refreshToken());
        if (!"refresh".equals(parsed.tokenType())) {
            throw new UnauthorizedException("Expected refresh token");
        }

        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenId(parsed.tokenId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        if (tokenEntity.getRevokedAt() != null
                || tokenEntity.getExpiresAt().isBefore(Instant.now())
                || !tokenEntity.getTokenHash().equals(Hashing.sha256Hex(request.refreshToken()))) {
            throw new UnauthorizedException("Refresh token invalid");
        }

        if (!tokenEntity.getUserId().equals(parsed.userId())) {
            throw new UnauthorizedException("Refresh token owner mismatch");
        }

        tokenEntity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(tokenEntity);

        UserEntity user = userRepository.findById(parsed.userId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User is not active");
        }

        return issueTokenPair(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        JwtService.ParsedToken parsed = jwtService.parse(request.refreshToken());
        if (!"refresh".equals(parsed.tokenType())) {
            throw new UnauthorizedException("Expected refresh token");
        }

        Optional<RefreshTokenEntity> token = refreshTokenRepository.findByTokenId(parsed.tokenId());
        token.ifPresent(entity -> {
            if (entity.getRevokedAt() == null && entity.getTokenHash().equals(Hashing.sha256Hex(request.refreshToken()))) {
                entity.setRevokedAt(Instant.now());
                refreshTokenRepository.save(entity);
            }
        });
    }

    @Transactional
    public CreateInviteResponse createInvite(CreateInviteRequest request, AuthenticatedUser actor) {
        int expiresInHours = request.expiresInHours() == null ? 24 : request.expiresInHours();
        if (expiresInHours < 1 || expiresInHours > 168) {
            throw new BadRequestException("expiresInHours must be between 1 and 168");
        }

        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        InviteEntity invite = new InviteEntity();
        invite.setEmail(request.email());
        invite.setTokenHash(Hashing.sha256Hex(token));
        invite.setExpiresAt(Instant.now().plus(expiresInHours, ChronoUnit.HOURS));
        invite.setCreatedBy(actor.userId());

        InviteEntity saved = inviteRepository.save(invite);
        return new CreateInviteResponse(saved.getId(), token, saved.getExpiresAt());
    }

    @Transactional
    public TokenResponse acceptInvite(AcceptInviteRequest request) {
        InviteEntity invite = inviteRepository.findByTokenHash(Hashing.sha256Hex(request.inviteToken()))
                .orElseThrow(() -> new BadRequestException("Invalid invite token"));

        if (invite.getAcceptedAt() != null) {
            throw new BadRequestException("Invite token already used");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Invite token has expired");
        }

        userRepository.findByEmail(invite.getEmail()).ifPresent(existing -> {
            throw new BadRequestException("User already exists for this email");
        });

        UserEntity user = new UserEntity();
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.pin()));
        user.setRole(UserRole.WORKER);
        user.setStatus(UserStatus.ACTIVE);

        UserEntity savedUser = userRepository.save(user);
        invite.setAcceptedAt(Instant.now());
        inviteRepository.save(invite);

        return issueTokenPair(savedUser);
    }

    private TokenResponse issueTokenPair(UserEntity user) {
        JwtService.TokenData accessToken = jwtService.generateAccessToken(user);
        JwtService.TokenData refreshToken = jwtService.generateRefreshToken(user);

        RefreshTokenEntity tokenEntity = new RefreshTokenEntity();
        tokenEntity.setTokenId(refreshToken.tokenId());
        tokenEntity.setTokenHash(Hashing.sha256Hex(refreshToken.token()));
        tokenEntity.setUserId(user.getId());
        tokenEntity.setExpiresAt(refreshToken.expiresAt());
        refreshTokenRepository.save(tokenEntity);

        return new TokenResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.token(),
                refreshToken.expiresAt()
        );
    }
}
