package org.example.leakybucket.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@RestController
@RequestMapping("/messages")
public class MessagesController {

    private static final Logger log = LoggerFactory.getLogger(MessagesController.class);

    private final SqsAsyncClient sqsClient;
    private final String queueUrl;

    public MessagesController(
        SqsAsyncClient sqsClient,
        @Value("${spring.cloud.aws.sqs.endpoint}") String endpoint,
        @Value("${app.sqs.queue-name:leaky-bucket}") String queue
    ) {
        this.sqsClient = sqsClient;
        // LocalStack account id is static; for real AWS this would be different
        this.queueUrl = String.format("%s/%s/%s", endpoint, "000000000000", queue);
    }

    @PostMapping("/publish/{count}")
    public ResponseEntity<String> publish(@PathVariable("count") int count) {
        if (count <= 0) {
            return ResponseEntity.badRequest().body("Count must be a positive integer");
        }
        List<CompletableFuture<?>> futures = new ArrayList<>(Math.min(count, 1000));
        for (int i = 1; i <= count; i++) {
            String payload = "message-" + i + "-" + UUID.randomUUID();
            SendMessageRequest req = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)
                .build();
            futures.add(sqsClient.sendMessage(req));
        }
        // Wait all to complete
        for (CompletableFuture<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while publishing messages", e);
                return ResponseEntity.internalServerError()
                    .body("Interrupted while publishing messages");
            } catch (ExecutionException e) {
                log.error("Error publishing messages to SQS", e);
                return ResponseEntity.internalServerError()
                    .body("Error publishing messages to SQS: " + e.getCause());
            }
        }
        return ResponseEntity.ok("Published " + count + " messages to queue");
    }
}
