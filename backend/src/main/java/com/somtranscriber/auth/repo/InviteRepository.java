package com.somtranscriber.auth.repo;

import com.somtranscriber.auth.model.InviteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<InviteEntity, UUID> {
    Optional<InviteEntity> findByTokenHash(String tokenHash);
}
