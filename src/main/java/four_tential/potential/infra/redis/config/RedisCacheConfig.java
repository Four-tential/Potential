package four_tential.potential.infra.redis.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static four_tential.potential.infra.redis.RedisConstants.INSTRUCTOR_PROFILE_CACHE;
import static four_tential.potential.infra.redis.RedisConstants.MY_FOLLOWS_CACHE;
import static four_tential.potential.infra.redis.RedisConstants.MY_PAGE_CACHE;
import static four_tential.potential.infra.redis.RedisConstants.REVIEW_LIST_CACHE;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(RedisSerializer.json())
                );

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        // 후기 목록 캐시: TTL 10분
        configs.put(REVIEW_LIST_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 강사 프로필 캐시: TTL 5분
        configs.put(INSTRUCTOR_PROFILE_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 팔로우 목록 캐시: TTL 5분
        configs.put(MY_FOLLOWS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 마이페이지 캐시: TTL 5분
        configs.put(MY_PAGE_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}