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

    public static final String CAPACITY_KEY = "app.bucket.capacity";
    private static final Logger log = LoggerFactory.getLogger(LimitUpdaterService.class);
    private final Bucket rateLimitBucket;
    private final RedissonClient redissonClient;

    @Value("${app.bucket.capacity:7200}")
    private int defaultCapacity;

    @Value("${app.bucket.period:PT1H}")
    private String period;

    private long lastKnownLimit;

    public LimitUpdaterService(Bucket rateLimitBucket, BucketConfiguration initialConfiguration,
        RedissonClient redissonClient) {
        this.rateLimitBucket = rateLimitBucket;
        this.redissonClient = redissonClient;

        this.lastKnownLimit = initialConfiguration.getBandwidths()[0].getCapacity();
    }

    @Scheduled(fixedDelay = 5000)
    public void checkAndApplyNewLimit() {
        int newLimit = fetchLimitFromRedisOrDefault();

        if (newLimit != lastKnownLimit) {
            log.info("Detected bucket limit change: {} -> {}", lastKnownLimit, newLimit);

            BucketConfiguration newConfig = createNewConfiguration(newLimit);

            try {
                rateLimitBucket.replaceConfiguration(newConfig, TokensInheritanceStrategy.AS_IS);

                this.lastKnownLimit = newLimit;
                log.info("Bucket configuration successfully updated to: {} per hour.", newLimit);

            } catch (Exception e) {
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
            log.debug("Could not read {} from Redis: {}", CAPACITY_KEY, e.getMessage());
        }
        return defaultCapacity;
    }

    private BucketConfiguration createNewConfiguration(int limitPerHour) {
        Duration duration = Duration.parse(period);
        Bandwidth bandwidth = BandwidthBuilder.builder()
            .capacity(limitPerHour)
            .refillGreedy(limitPerHour, duration)
            .initialTokens(0L).build();
        return BucketConfiguration.builder().addLimit(bandwidth).build();

    }
}