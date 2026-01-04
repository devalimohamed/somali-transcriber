package com.somtranscriber.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.Optional;

@Service
public class RetryQueueService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String queueKey;

    public RetryQueueService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.queueKey = appProperties.retry().queueKey();
    }

    public void enqueue(RetryJob job) {
        try {
            String payload = objectMapper.writeValueAsString(job);
            double score = job.availableAt() == null
                    ? Instant.now().toEpochMilli()
                    : job.availableAt().toEpochMilli();
            redisTemplate.opsForZSet().add(queueKey, payload, score);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to enqueue retry job", exception);
        }
    }

    public Optional<RetryJob> pollReadyJob() {
        try {
            Set<ZSetOperations.TypedTuple<String>> earliest = redisTemplate.opsForZSet()
                    .rangeWithScores(queueKey, 0, 0);
            if (earliest == null || earliest.isEmpty()) {
                return Optional.empty();
            }

            ZSetOperations.TypedTuple<String> tuple = earliest.iterator().next();
            String payload = tuple.getValue();
            Double score = tuple.getScore();
            if (payload == null || score == null) {
                return Optional.empty();
            }

            if (score > Instant.now().toEpochMilli()) {
                return Optional.empty();
            }

            Long removed = redisTemplate.opsForZSet().remove(queueKey, payload);
            if (removed == null || removed == 0) {
                return Optional.empty();
            }

            RetryJob job = objectMapper.readValue(payload, RetryJob.class);
            return Optional.of(job);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to poll retry queue", exception);
        }
    }
}
