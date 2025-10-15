package org.example.leakybucket.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DistributedBucketProvider {

    private static final String BUCKET_KEY = "sqs-global-rate-limit-key";

    private final ProxyManager<String> distributedProxyManager;
    private final BucketConfiguration sharedBucketConfiguration;

    public DistributedBucketProvider(ProxyManager<String> distributedProxyManager,
        BucketConfiguration sharedBucketConfiguration) {
        this.distributedProxyManager = distributedProxyManager;
        this.sharedBucketConfiguration = sharedBucketConfiguration;
    }

    @Bean
    public Bucket rateLimitBucket() {
        // The ProxyManager resolves the Bucket, using the provided configuration
        // if the bucket key is not yet present in Redis.
        return distributedProxyManager.builder().build(
            BUCKET_KEY,
            () -> sharedBucketConfiguration
        );
    }
}