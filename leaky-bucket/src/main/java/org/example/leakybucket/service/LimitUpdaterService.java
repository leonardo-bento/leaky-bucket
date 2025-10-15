package org.example.leakybucket.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.TokensInheritanceStrategy;
import java.time.Duration;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LimitUpdaterService {

    private static final Logger log = LoggerFactory.getLogger(LimitUpdaterService.class);

    public static final String CAPACITY_KEY = "app.bucket.capacity";

    private final Bucket rateLimitBucket;
    private final BucketConfiguration initialConfiguration;
    private final RedissonClient redissonClient;

    @Value("${app.bucket.capacity:7200}")
    private int defaultCapacity;

    @Value("${app.bucket.period:PT1H}")
    private String period;

    // Cache the last known limit to avoid unnecessary Redis calls
    private int lastKnownLimit;

    public LimitUpdaterService(Bucket rateLimitBucket, BucketConfiguration initialConfiguration, RedissonClient redissonClient) {
        this.rateLimitBucket = rateLimitBucket;
        this.initialConfiguration = initialConfiguration;
        this.redissonClient = redissonClient;

        // Initialize the last known limit from the current configuration
        this.lastKnownLimit = (int) initialConfiguration.getBandwidths()[0].getCapacity();
    }

    /**
     * Runs periodically to check for feature flag updates (every 5 seconds by default).
     */
    @Scheduled(fixedDelay = 5000)
    public void checkAndApplyNewLimit() {
        int newLimit = fetchLimitFromRedisOrDefault();

        if (newLimit != lastKnownLimit) {
            log.info("Detected bucket limit change: {} -> {}", lastKnownLimit, newLimit);

            // 1. Create the NEW Bucket Configuration
            BucketConfiguration newConfig = createNewConfiguration(newLimit);

            try {
                // 2. Atomically REPLACE the configuration in Redis
                rateLimitBucket.replaceConfiguration(newConfig, TokensInheritanceStrategy.AS_IS
                    // Crucial: Keep the current token count
                );

                this.lastKnownLimit = newLimit;
                log.info("Bucket configuration successfully updated to: {} per hour.", newLimit);

            } catch (Exception e) {
                // Handle potential distributed lock or Redis connection errors
                log.error("Failed to replace Bucket4j configuration: {}", e.getMessage());
            }
        }
    }

    private int fetchLimitFromRedisOrDefault() {
        try {
            RBucket<Integer> bucket = redissonClient.getBucket(CAPACITY_KEY);
            Integer value = bucket.get();
            if (value != null && value > 0) {
                return value;
            }
        } catch (Exception e) {
            // Redis might be temporarily unavailable; fall back gracefully
            log.debug("Could not read {} from Redis: {}", CAPACITY_KEY, e.getMessage());
        }
        return defaultCapacity;
    }

    /**
     * Helper to create the new configuration structure (Capacity=1, Refill=newLimit/hr).
     */
    private BucketConfiguration createNewConfiguration(int limitPerHour) {
        Duration duration = Duration.parse(period);
        Bandwidth bandwidth = BandwidthBuilder.builder().capacity(1L)
            .refillGreedy(limitPerHour, duration).initialTokens(0L).build();
        return BucketConfiguration.builder().addLimit(bandwidth).build();

    }
}