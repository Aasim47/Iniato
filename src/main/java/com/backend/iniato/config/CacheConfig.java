package com.backend.iniato.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Turns Spring's caching on and points it at Redis.
 *
 * <p>Every cache here exists to keep a query off Postgres. On Neon's free plan
 * compute is the scarce resource (100 CU-hours/month), so a cache hit is not
 * just faster — it is a query that never wakes or bills the database.
 *
 * <p><b>Why TTLs are short.</b> Nothing here is invalidated by a write on
 * another instance, so every TTL is the worst-case staleness window. They are
 * deliberately sized to the damage a stale entry can do:
 *
 * <ul>
 *   <li>{@code users} (10 min) — identity for auth. Explicitly evicted on every
 *       write path that touches a User ({@code UserLookupService.evict}), so the
 *       TTL only matters if an eviction is missed.</li>
 *   <li>{@code nearbyRoutes} (8 s) — carries live {@code availableSeats}. Long
 *       enough to collapse a crowd of pollers into one query, short enough that
 *       a rider never sees a seat that filled up a moment ago. This is the
 *       stale-data risk the old {@code @Cacheable} had, now bounded by seconds
 *       instead of minutes.</li>
 * </ul>
 *
 * <p>If Redis is not reachable the app still starts: {@link #cacheManager}
 * yields to an in-memory manager rather than failing. An in-memory cache is
 * per-instance, which is fine at one instance and wrong at several — see the
 * fallback's own note.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /** Identity lookups behind every authenticated request. */
    public static final String USERS_CACHE = "users";

    /** Rider-facing route discovery — a polling endpoint. */
    public static final String NEARBY_ROUTES_CACHE = "nearbyRoutes";

    private static final Duration USERS_TTL = Duration.ofMinutes(10);
    private static final Duration NEARBY_ROUTES_TTL = Duration.ofSeconds(8);

    /**
     * The Redis-backed cache manager.
     *
     * <p>Conditional on a {@link RedisConnectionFactory} being present, so the
     * in-memory fallback below takes over when Redis is not configured at all.
     * Deliberately not {@code @Primary}: a test that supplies its own
     * {@code @Primary} manager should override this, and two {@code @Primary}
     * beans of one type is an error rather than an override.
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                // A null is a real answer ("no such user") and worth caching —
                // but Spring cannot store one, so misses stay uncached.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        USERS_CACHE, defaults.entryTtl(USERS_TTL),
                        NEARBY_ROUTES_CACHE, defaults.entryTtl(NEARBY_ROUTES_TTL)))
                .build();
    }

    /**
     * Treat a cache failure as a cache miss.
     *
     * <p>Without this, Spring lets a Redis error propagate out of the
     * {@code @Cacheable} method — so a Redis outage turns a working endpoint
     * into an HTTP 500. That was verified, not assumed: with Redis stopped,
     * {@code GET /api/routes/nearby} answered
     * {@code 500 "Unable to connect to Redis"}.
     *
     * <p>The cache exists to spare the database work, and the database is still
     * there. Losing Redis should cost speed and extra queries, never
     * correctness — so reads fall through to the real method and writes are
     * dropped. Failures are logged at WARN, since a silently dead cache is its
     * own kind of outage on the free tier.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private final Logger log = LoggerFactory.getLogger(CacheConfig.class);

            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache '{}' unavailable for get [{}] — falling through to the database: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache '{}' unavailable for put [{}]: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                // An eviction that never happened is the one case with a real
                // consequence: a stale entry survives until its TTL. Both TTLs
                // here are short by design, which is what makes this tolerable.
                log.warn("Cache '{}' unavailable for evict [{}] — entry will expire via TTL: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache '{}' unavailable for clear: {}", cache.getName(), ex.getMessage());
            }
        };
    }

    /**
     * Cached values are polymorphic DTOs, so the serializer needs type
     * information to read them back. Scoped to a private mapper — the app's own
     * HTTP {@code ObjectMapper} must not start emitting {@code @class} fields.
     */
    private ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    /**
     * Fallback when Redis is not configured at all.
     *
     * <p>Caches live in this JVM's heap, so two instances can disagree and an
     * eviction on one is invisible to the other. Acceptable for local dev and a
     * single-instance free-tier deploy; revisit before scaling out.
     *
     * <p>Note this is about Redis being <em>absent from the configuration</em>,
     * not merely unreachable. A configured-but-down Redis still uses the manager
     * above, where {@link #errorHandler()} turns each failure into a miss.
     */
    @Bean
    @ConditionalOnMissingBean({CacheManager.class, RedisConnectionFactory.class})
    public CacheManager inMemoryCacheManager() {
        return new ConcurrentMapCacheManager(USERS_CACHE, NEARBY_ROUTES_CACHE);
    }
}
