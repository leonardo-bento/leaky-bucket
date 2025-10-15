package org.example.leakybucket.web;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
public class ConfigController {

    public static final String CAPACITY_KEY = "app.bucket.capacity";

    private final RedissonClient redissonClient;

    public ConfigController(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Sets the peak Bucket limit (tokens per period) and stores it in Redis under the key
     * "app.bucket.capacity". The LimitUpdaterService will pick this value and update the distributed
     * bucket configuration accordingly.
     */
    @PostMapping("/limit/{limit}")
    public ResponseEntity<String> updateBucketLimit(@PathVariable("limit") int limit) {
        if (limit <= 0) {
            return ResponseEntity.badRequest().body("Limit must be a positive integer");
        }
        RBucket<Integer> bucket = redissonClient.getBucket(CAPACITY_KEY);
        bucket.set(limit);
        return ResponseEntity.ok("Stored app.bucket.capacity=" + limit);
    }
}
