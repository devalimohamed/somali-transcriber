package com.somtranscriber.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.model.JobStage;
import com.somtranscriber.processing.service.RetryJob;
import com.somtranscriber.processing.service.RetryQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RetryQueueService retryQueueService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("issuer", 15, 30, "secret-secret-secret-secret-secret-secret"),
                new AppProperties.Audio("/tmp/audio", 120),
                new AppProperties.OpenAi("", "gpt-4o-transcribe", "gpt-4o-mini", "https://api.openai.com"),
                new AppProperties.Ollama("", "qwen2.5:3b"),
                new AppProperties.Retry("retry-test-queue", 3, true),
                new AppProperties.Cors(List.of("http://localhost"))
        );

        retryQueueService = new RetryQueueService(redisTemplate, objectMapper, properties);
    }

    @Test
    void enqueueStoresJobInSortedSet() {
        Instant availableAt = Instant.now().plusSeconds(5);
        RetryJob job = new RetryJob(UUID.randomUUID(), JobStage.TRANSCRIPTION, 1, availableAt);

        retryQueueService.enqueue(job);

        verify(zSetOperations).add(eq("retry-test-queue"), any(String.class), eq((double) availableAt.toEpochMilli()));
    }

    @Test
    void pollReadyJobReturnsEmptyWhenNoJobsExist() {
        when(zSetOperations.rangeWithScores("retry-test-queue", 0, 0)).thenReturn(Set.of());

        assertThat(retryQueueService.pollReadyJob()).isEmpty();
    }

    @Test
    void pollReadyJobSkipsWhenEarliestJobIsNotReady() throws Exception {
        RetryJob job = new RetryJob(UUID.randomUUID(), JobStage.TRANSCRIPTION, 1, Instant.now().plusSeconds(60));
        String payload = objectMapper.writeValueAsString(job);
        Set<ZSetOperations.TypedTuple<String>> tuples = Set.of(
                new DefaultTypedTuple<>(payload, (double) Instant.now().plusSeconds(60).toEpochMilli())
        );
        when(zSetOperations.rangeWithScores("retry-test-queue", 0, 0)).thenReturn(tuples);

        assertThat(retryQueueService.pollReadyJob()).isEmpty();
        verify(zSetOperations, never()).remove("retry-test-queue", payload);
    }

    @Test
    void pollReadyJobClaimsAndReturnsReadyJob() throws Exception {
        RetryJob job = new RetryJob(UUID.randomUUID(), JobStage.FORMATTER, 2, Instant.now().minusSeconds(1));
        String payload = objectMapper.writeValueAsString(job);
        Set<ZSetOperations.TypedTuple<String>> tuples = Set.of(
                new DefaultTypedTuple<>(payload, (double) Instant.now().minusSeconds(1).toEpochMilli())
        );
        when(zSetOperations.rangeWithScores("retry-test-queue", 0, 0)).thenReturn(tuples);
        when(zSetOperations.remove("retry-test-queue", payload)).thenReturn(1L);

        Optional<RetryJob> result = retryQueueService.pollReadyJob();

        assertThat(result).isPresent();
        assertThat(result.get().callId()).isEqualTo(job.callId());
        assertThat(result.get().stage()).isEqualTo(JobStage.FORMATTER);
    }
}
