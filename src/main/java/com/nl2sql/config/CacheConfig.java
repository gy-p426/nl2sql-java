package com.nl2sql.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(NL2SQLProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "keywords", "sqlResults", "schemas"
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(properties.getSettings().getCacheMaxSize())
            .expireAfterWrite(properties.getSettings().getCacheExpireMinutes(), TimeUnit.MINUTES)
            .recordStats());
        
        return cacheManager;
    }
}
