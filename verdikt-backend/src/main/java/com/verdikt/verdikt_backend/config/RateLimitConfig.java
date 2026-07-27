package com.verdikt.verdikt_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitConfig {

    private final Cache<String, Bucket> roomCreationBuckets;
    private final Cache<String, Bucket> voteBuckets;

    public RateLimitConfig(
            @Value("${app.rate-limit.room-creation-ttl-minutes:120}") int roomCreationTtlMinutes,
            @Value("${app.rate-limit.vote-ttl-minutes:5}") int voteTtlMinutes
    ) {
        this.roomCreationBuckets = Caffeine.newBuilder()
                .expireAfterAccess(roomCreationTtlMinutes, TimeUnit.MINUTES)
                .build();

        this.voteBuckets = Caffeine.newBuilder()
                .expireAfterAccess(voteTtlMinutes, TimeUnit.MINUTES)
                .build();
    }

    public Bucket resolveRoomCreationBucket(String ip) {
        return roomCreationBuckets.get(ip, k -> newRoomCreationBucket());
    }

    public Bucket resolveVoteBucket(String key) {
        return voteBuckets.get(key, k -> newVoteBucket());
    }

    private Bucket newRoomCreationBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newVoteBucket() {
        Bandwidth limit = Bandwidth.classic(30, Refill.intervally(30, Duration.ofSeconds(10)));
        return Bucket.builder().addLimit(limit).build();
    }
}