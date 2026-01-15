package com.somtranscriber.processing;

import com.somtranscriber.processing.model.JobStage;
import com.somtranscriber.processing.service.ProcessingService;
import com.somtranscriber.processing.service.RetryJob;
import com.somtranscriber.processing.service.RetryQueueService;
import com.somtranscriber.processing.service.RetryWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryWorkerTest {

    @Mock
    private RetryQueueService retryQueueService;

    @Mock
    private ProcessingService processingService;

    @Test
    void pollAndProcessJobsStopsWhenQueueIsEmpty() {
        RetryWorker retryWorker = new RetryWorker(retryQueueService, processingService);
        when(retryQueueService.pollReadyJob()).thenReturn(Optional.empty());

        retryWorker.pollAndProcessJobs();

        verify(retryQueueService, times(1)).pollReadyJob();
    }

    @Test
    void pollAndProcessJobsProcessesMultipleJobsInBatch() {
        RetryWorker retryWorker = new RetryWorker(retryQueueService, processingService);
        RetryJob first = new RetryJob(UUID.randomUUID(), JobStage.TRANSCRIPTION, 1, Instant.now());
        RetryJob second = new RetryJob(UUID.randomUUID(), JobStage.FORMATTER, 2, Instant.now());

        when(retryQueueService.pollReadyJob())
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(second))
                .thenReturn(Optional.empty());

        retryWorker.pollAndProcessJobs();

        verify(processingService).processRetryJob(first);
        verify(processingService).processRetryJob(second);
        verify(retryQueueService, times(3)).pollReadyJob();
    }

    @Test
    void pollAndProcessJobsContinuesAfterProcessingError() {
        RetryWorker retryWorker = new RetryWorker(retryQueueService, processingService);
        RetryJob failed = new RetryJob(UUID.randomUUID(), JobStage.FORMATTER, 1, Instant.now());

        when(retryQueueService.pollReadyJob())
                .thenReturn(Optional.of(failed))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("boom")).when(processingService).processRetryJob(failed);

        retryWorker.pollAndProcessJobs();

        verify(processingService).processRetryJob(failed);
        verify(retryQueueService, times(2)).pollReadyJob();
    }
}
