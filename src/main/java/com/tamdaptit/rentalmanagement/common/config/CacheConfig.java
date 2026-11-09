package com.tamdaptit.rentalmanagement.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tamdaptit.rentalmanagement.common.cache.ResilientCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory,
            @Value("${app.cache.ttl:30m}") Duration ttl) {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.tamdaptit.rentalmanagement.")
                .allowIfSubType("java.util.").allowIfSubType("java.lang.").allowIfSubType("java.time.").build(),
                ObjectMapper.DefaultTyping.EVERYTHING,JsonTypeInfo.As.PROPERTY);
        var config = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> "rental:v1:"+name+"::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)))
                .entryTtl(ttl).disableCachingNullValues();
        // Spring Data 4.1 defaults to asynchronous writes; invalidation must finish before returning.
        var writer = RedisCacheWriter.create(factory, options -> options
                .batchStrategy(BatchStrategies.scan(100)).immediateWrites());
        var redis = RedisCacheManager.builder(writer).cacheDefaults(config)
                .initialCacheNames(Set.of("amenities","roomTypes","propertyTypes","provinces","wards")).build();
        redis.afterPropertiesSet();
        return new CacheManager() {
            private final Map<String,Cache> caches = new ConcurrentHashMap<>();
            public Cache getCache(String name) {
                return caches.computeIfAbsent(name, key -> {
                    Cache target = redis.getCache(key);
                    // Resilience also applies to operations deferred until after transaction commit.
                    return target==null ? null : new TransactionAwareCacheDecorator(new ResilientCache(target));
                });
            }
            public Collection<String> getCacheNames() { return redis.getCacheNames(); }
        };
    }
}
