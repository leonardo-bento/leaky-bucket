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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Bucket4jConfiguration {

    public static final long BUCKET_INITIAL_TOKENS = 0L;
    private static final String CACHE_NAME = "bucket4j-leaky-bucket-cache";
    private static final int MAX_BANDWIDTH_CAPACITY_SIMULTANEOUSLY = 1;
    private final RedissonClient redissonClient;

    public Bucket4jConfiguration(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Bean
    public BucketConfiguration sharedBucketConfiguration(
        @Value("${app.bucket.capacity}") long capacity,
        @Value("${app.bucket.period}") String period
    ) {
        Duration duration = Duration.parse(period);
        Bandwidth bandwidth = BandwidthBuilder.builder()
            .capacity(MAX_BANDWIDTH_CAPACITY_SIMULTANEOUSLY)
            .refillGreedy(capacity, duration)
            .initialTokens(BUCKET_INITIAL_TOKENS)
            .build();
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build();
    }

    @Bean
    public CacheManager cacheManager() {
        javax.cache.spi.CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();
        javax.cache.configuration.Configuration<Object, Object> redissonConfig =
            RedissonConfiguration.fromInstance(redissonClient);
        cacheManager.createCache(CACHE_NAME, redissonConfig);

        return cacheManager;
    }

    @Bean
    public ProxyManager<String> distributedProxyManager(CacheManager cacheManager) {
        javax.cache.Cache<?, ?> untypedCache = cacheManager.getCache(CACHE_NAME);
        @SuppressWarnings("unchecked")
        javax.cache.Cache<String, byte[]> cache = (javax.cache.Cache<String, byte[]>) untypedCache;

        return Bucket4jJCache.entryProcessorBasedBuilder(cache).build();
    }
}
