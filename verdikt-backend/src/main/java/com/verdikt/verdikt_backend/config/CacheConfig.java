package com.verdikt.verdikt_backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                CacheConstants.ROOM_BY_CODE,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)),
                CacheConstants.PLAYER_BY_TOKEN,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(30)),
                CacheConstants.VOTE_STATE,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(1)),
                CacheConstants.ACTIVE_QUESTION,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)),
                CacheConstants.REPORT_CARD,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(24)),
                CacheConstants.NON_CUSTOM_QUESTIONS,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
