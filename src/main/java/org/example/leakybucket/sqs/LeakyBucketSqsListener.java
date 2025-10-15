package org.example.leakybucket.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LeakyBucketSqsListener {

    private static final Logger log = LoggerFactory.getLogger(LeakyBucketSqsListener.class);

    private final Bucket bucket;

    public LeakyBucketSqsListener(Bucket bucket) {
        this.bucket = bucket;
    }

    @SqsListener("${app.sqs.queue-name:leaky-bucket}")
    public void onMessage(String payload) throws InterruptedException {
        BlockingBucket blockingBucket = bucket.asBlocking();
        try {
            blockingBucket.consume(1L);
            log.info("Processing message: {}", payload);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Consumer thread interrupted while waiting for rate limit token.");
        }
    }
}
