package org.example.leakybucket.sqs;

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
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@Profile("manual")
public class LeakyBucketSqsManual {

    private static final Logger log = LoggerFactory.getLogger(LeakyBucketSqsManual.class);

    private final SqsAsyncClient sqsClient;
    private final Bucket bucket;
    private final String queueUrl;

    // Metrics
    private final Counter messagesProcessedCounter;
    private final Counter messagesEmptyCounter;
    private final Counter messagesErrorCounter;
    private final Timer messageProcessingTimer;
    private final Counter schedulerProcessed;

    private final ThreadPoolTaskExecutor threadPool;

    public LeakyBucketSqsManual(
        Bucket bucket,
        SqsAsyncClient sqsClient,
        MeterRegistry meterRegistry,
        @Value("${spring.cloud.aws.sqs.endpoint}") String endpoint,
        @Value("${app.sqs.queue-name:leaky-bucket}") String queue) {
        this.bucket = bucket;
        this.sqsClient = sqsClient;

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
        this.schedulerProcessed = meterRegistry.counter(
            "leakybucket_sqs_manual_scheduler_total");

        this.queueUrl = String.format("%s/%s/%s", endpoint, "000000000000", queue);

        this.threadPool = new ThreadPoolTaskExecutor();
        this.threadPool.setCorePoolSize(10);
        this.threadPool.setMaxPoolSize(20);
        this.threadPool.setThreadNamePrefix("leaky-thread-");
        this.threadPool.initialize();
    }

    @Scheduled(fixedRate = 1000)
    public void onSchedule() {
        log.info("Trying to consume some Bucket Token");
        this.schedulerProcessed.increment();

        long tokensAvailable = bucket.getAvailableTokens();
        while (tokensAvailable > 0L) {
            int tokensToGet = Math.toIntExact(tokensAvailable >= 10L ? 10 : tokensAvailable);
            pullAndProcess(tokensToGet);
            tokensAvailable = bucket.getAvailableTokens();
        }
        log.info("Ending to consume some Bucket Token");
    }

    private DeleteMessageRequest deleteMessages(Message message) {
        return DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build();
    }

    private void pullAndProcess(int tokensToGet) {
        long start = System.nanoTime();
        try {
            bucket.asBlocking().consume(tokensToGet);
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(tokensToGet)
                .waitTimeSeconds(0)
                .build();

            List<Message> messages = sqsClient.receiveMessage(request).get().messages();
            if (messages.isEmpty()) {
                messagesEmptyCounter.increment();
                bucket.addTokens(tokensToGet);
                return;
            }

            messages.forEach(message -> this.threadPool.execute(() -> processMessage(message)));

            long end = System.nanoTime();
            messageProcessingTimer.record(Duration.ofNanos(end - start));
            messagesProcessedCounter.increment(messages.size());
        } catch (ExecutionException e) {
            messagesErrorCounter.increment();
            log.error("Error while interacting with SQS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            messagesErrorCounter.increment();
            log.error("Consumer thread interrupted while waiting for rate limit token.");
        }
    }

    private void processMessage(Message message) {
        try {
            Thread.sleep(250);
            log.info("Processing message: {}", message.body());
            sqsClient.deleteMessage(deleteMessages(message)).get();
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
