package com.example.urlshortener.link;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class LinkCache {

    private static final String PREFIX = "link:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public LinkCache(StringRedisTemplate redis,
                     @Value("${app.cache.link-ttl-minutes}") long ttlMinutes) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<String> get(String shortCode) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + shortCode));
        } catch (RuntimeException e) {
            log.warn("Redis read failed, falling back to database: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String shortCode, String originalUrl) {
        try {
            redis.opsForValue().set(PREFIX + shortCode, originalUrl, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis write failed: {}", e.getMessage());
        }
    }

    public void evict(String shortCode) {
        try {
            redis.delete(PREFIX + shortCode);
        } catch (RuntimeException e) {
            log.warn("Redis evict failed: {}", e.getMessage());
        }
    }
}
