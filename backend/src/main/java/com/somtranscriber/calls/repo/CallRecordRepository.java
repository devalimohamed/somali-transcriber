package com.somtranscriber.calls.repo;

import com.somtranscriber.calls.model.CallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallRecordRepository extends JpaRepository<CallRecordEntity, UUID> {
    Optional<CallRecordEntity> findByIdAndUserId(UUID id, UUID userId);

    List<CallRecordEntity> findByUserIdAndCallAtBetweenOrderByCallAtDesc(UUID userId, Instant from, Instant to);

    List<CallRecordEntity> findByUserIdOrderByCallAtDesc(UUID userId);
}
