package org.example.leakybucket.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BucketConfiguration {

    @Bean
    public Bucket rateLimitBucket(
        @Value("${app.bucket.capacity:120}") long capacity,
        @Value("${app.bucket.period:PT1H}") String period
    ) {
        Duration duration = Duration.parse(period);
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, duration)
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}
