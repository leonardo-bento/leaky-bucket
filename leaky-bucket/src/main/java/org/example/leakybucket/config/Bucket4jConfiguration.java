package org.example.leakybucket.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfiguration {

    private static final int MAX_BANDWIDTH_CAPACITY_SIMULTANEOUSLY = 1;

    @Bean
    public Bucket rateLimitBucket(
        @Value("${app.bucket.capacity:7200}") long capacity,
        @Value("${app.bucket.period:PT1H}") String period
    ) {
        Duration duration = Duration.parse(period);
        Bandwidth bandwidth = BandwidthBuilder.builder()
            .capacity(MAX_BANDWIDTH_CAPACITY_SIMULTANEOUSLY)
            .refillGreedy(capacity, duration)
            .initialTokens(0L)
            .build();
        return Bucket.builder()
            .addLimit(bandwidth)
            .withMillisecondPrecision()
            .build();
    }
}
