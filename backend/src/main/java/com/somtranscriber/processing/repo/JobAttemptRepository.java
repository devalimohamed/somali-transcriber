package com.somtranscriber.processing.repo;

import com.somtranscriber.processing.model.JobAttemptEntity;
import com.somtranscriber.processing.model.JobStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttemptEntity, UUID> {
    Optional<JobAttemptEntity> findTopByCallIdAndStageOrderByAttemptNoDesc(UUID callId, JobStage stage);
}
