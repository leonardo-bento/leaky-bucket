package org.example.leakybucket.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.grid.jcache.Bucket4jJCache;
import java.time.Duration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import org.redisson.api.RedissonClient;
import org.redisson.jcache.configuration.RedissonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfiguration {

    public static final String CACHE_NAME = "rate-limit-cache";
    private static final int MAX_BANDWIDTH_CAPACITY_SIMULTANEOUSLY = 1;

    @Autowired
    private RedissonClient redissonClient;

    @Bean
    public BucketConfiguration sharedBucketConfiguration(
        @Value("${app.bucket.capacity:7200}") long capacity,
        @Value("${app.bucket.period:PT1H}") String period
    ) {
        Duration duration = Duration.parse(period);
        Bandwidth bandwidth = BandwidthBuilder.builder()
            .capacity(MAX_BANDWIDTH_CAPACITY_SIMULTANEOUSLY)
            .refillGreedy(capacity, duration)
            .initialTokens(0L)
            .build();
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build();
    }

    @Bean
    public CacheManager cacheManager() {
        // Get the default JCache CachingProvider
        javax.cache.spi.CachingProvider provider = Caching.getCachingProvider();

        // Create the CacheManager
        CacheManager cacheManager = provider.getCacheManager();

        // Define the cache properties using Redisson
        javax.cache.configuration.Configuration<Object, Object> redissonConfig =
            RedissonConfiguration.fromInstance(redissonClient);

        // Create the cache used by Bucket4j
        cacheManager.createCache(CACHE_NAME, redissonConfig);

        return cacheManager;
    }

    @Bean
    public ProxyManager<String> distributedProxyManager(CacheManager cacheManager) {
        // Retrieve cache without enforcing key/value types to avoid provider-specific type checks
        javax.cache.Cache<?, ?> untypedCache = cacheManager.getCache(CACHE_NAME);
        @SuppressWarnings("unchecked")
        javax.cache.Cache<String, byte[]> cache = (javax.cache.Cache<String, byte[]>) untypedCache;

        // Bucket4j uses the JCache instance to store and atomically update the token count.
        return Bucket4jJCache.entryProcessorBasedBuilder(cache).build();
    }
}
