package org.example.leakybucket.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Component
@Profile("automatic")
public class LeakyBucketSqsListener {

    private static final Logger log = LoggerFactory.getLogger(LeakyBucketSqsListener.class);

    private final Bucket bucket;

    // Metrics
    private final Counter messagesProcessedCounter;
    private final Counter messagesEmptyCounter;
    private final Counter messagesErrorCounter;
    private final Timer messageProcessingTimer;

    public LeakyBucketSqsListener(
        Bucket bucket,
        SqsAsyncClient sqsClient,
        MeterRegistry meterRegistry) {
        this.bucket = bucket;

        // Initialize metrics
        this.messagesProcessedCounter = meterRegistry.counter(
            "leakybucket_sqs_messages_total", "result", "success");
        this.messagesEmptyCounter = meterRegistry.counter(
            "leakybucket_sqs_messages_total", "result", "empty");
        this.messagesErrorCounter = meterRegistry.counter(
            "leakybucket_sqs_messages_total", "result", "error");
        this.messageProcessingTimer = Timer.builder("leakybucket_sqs_processing_duration_seconds")
            .description("Time taken to process a single SQS message")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(meterRegistry);
    }

    @SqsListener("${app.sqs.queue-name:leaky-bucket}")
    public void onMessage(String payload) throws InterruptedException {
        BlockingBucket blockingBucket = bucket.asBlocking();
        try {
            blockingBucket.consume(1L);
            long start = System.nanoTime();
            Thread.sleep(500);
            log.info("Processing message: {}", payload);
            long end = System.nanoTime();
            messageProcessingTimer.record(Duration.ofNanos(end - start));
            messagesProcessedCounter.increment();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Consumer thread interrupted while waiting for rate limit token.");
            messagesErrorCounter.increment();
        }
    }
}
