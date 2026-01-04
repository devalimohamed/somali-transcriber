package com.somtranscriber.processing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(value = "app.retry.worker.enabled", havingValue = "true", matchIfMissing = true)
public class RetryWorker {

    private static final Logger log = LoggerFactory.getLogger(RetryWorker.class);

    private final RetryQueueService retryQueueService;
    private final ProcessingService processingService;

    public RetryWorker(RetryQueueService retryQueueService, ProcessingService processingService) {
        this.retryQueueService = retryQueueService;
        this.processingService = processingService;
    }

    @Scheduled(fixedDelayString = "${app.retry.worker.delay-ms:2000}")
    public void pollAndProcessJobs() {
        for (int i = 0; i < 5; i++) {
            Optional<RetryJob> job = retryQueueService.pollReadyJob();
            if (job.isEmpty()) {
                return;
            }

            try {
                processingService.processRetryJob(job.get());
            } catch (Exception exception) {
                log.error("Retry job failed: {}", job.get(), exception);
            }
        }
    }
}
