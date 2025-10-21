package org.example.leakybucket.sqs;

import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
public class LeakyBucketSqsListener {

    private static final Logger log = LoggerFactory.getLogger(LeakyBucketSqsListener.class);

    private final SqsAsyncClient sqsClient;
    private final Bucket bucket;
    private final String endpoint;
    private final String queue;

    // Metrics
    private final Counter messagesProcessedCounter;
    private final Counter messagesEmptyCounter;
    private final Counter messagesErrorCounter;
    private final Timer messageProcessingTimer;

    public LeakyBucketSqsListener(
        Bucket bucket,
        SqsAsyncClient sqsClient,
        MeterRegistry meterRegistry,
        @Value("${spring.cloud.aws.sqs.endpoint}") String endpoint,
        @Value("${app.sqs.queue-name:leaky-bucket}") String queue) {
        this.bucket = bucket;
        this.sqsClient = sqsClient;
        this.endpoint  = endpoint;
        this.queue = queue;

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

//    @SqsListener("${app.sqs.queue-name:leaky-bucket}")
//    public void onMessage(String payload) throws InterruptedException {
//        BlockingBucket blockingBucket = bucket.asBlocking();
//        try {
//            blockingBucket.consume(1L);
//            log.info("Processing message: {}", payload);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            log.error("Consumer thread interrupted while waiting for rate limit token.");
//        }
//    }

    @Scheduled(fixedRate = 100)
    public void onSchedule() {
        String queueUrl = String.format("%s/%s/%s", endpoint, "000000000000", queue);
        if (!bucket.tryConsume(1L)) {
            return;
        }

        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(0)
                .build();

            List<Message> messages = sqsClient.receiveMessage(request).get().messages();
            if (messages.isEmpty()) {
                messagesEmptyCounter.increment();
                return;
            }
            Message message = messages.get(0);
            log.info("Processing message: {}", message.body());

            long start = System.nanoTime();
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();
            sqsClient.deleteMessage(deleteRequest).get();
            long end = System.nanoTime();
            messageProcessingTimer.record(Duration.ofNanos(end - start));
            messagesProcessedCounter.increment();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            messagesErrorCounter.increment();
            log.error("Consumer thread interrupted while waiting for rate limit token.");
        } catch (ExecutionException e) {
            messagesErrorCounter.increment();
            log.error("Error while interacting with SQS", e);
        }
    }
}
