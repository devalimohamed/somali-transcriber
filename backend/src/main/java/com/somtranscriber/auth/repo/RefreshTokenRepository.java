package com.somtranscriber.auth.repo;

import com.somtranscriber.auth.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenId(String tokenId);

    List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);
}
