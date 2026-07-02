package com.verdikt.verdikt_backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitConfig {

    // ip -> bucket, separate maps per action type
    private final ConcurrentMap<String, Bucket> roomCreationBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> voteBuckets = new ConcurrentHashMap<>();

    public Bucket resolveRoomCreationBucket(String ip) {
        return roomCreationBuckets.computeIfAbsent(ip, k -> newRoomCreationBucket());
    }

    public Bucket resolveVoteBucket(String ip) {
        return voteBuckets.computeIfAbsent(ip, k -> newVoteBucket());
    }

    private Bucket newRoomCreationBucket() {
        // max 5 rooms per hour per IP
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newVoteBucket() {
    // 30 votes per 10 seconds — enough for free toggle without spam
    Bandwidth limit = Bandwidth.classic(30, Refill.intervally(30, Duration.ofSeconds(10)));
    return Bucket.builder().addLimit(limit).build();
}
}