package com.backend.iniato.config;

import com.backend.iniato.dto.RouteResponseDTO;
import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.*;
import com.backend.iniato.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Proves the caching annotations are actually wired — that a second identical
 * call is served from the cache instead of the database.
 *
 * <p>Runs against an in-memory {@code CacheManager} rather than Redis, because
 * the thing most likely to be silently broken is not Redis itself but the
 * plumbing around it: a missing {@code @EnableCaching}, or a self-invocation
 * that bypasses the Spring proxy so {@code @Cacheable} never runs. Both would
 * leave every query hitting the database while looking perfectly fine.
 *
 * <p>Redis-specific serialization is covered separately by {@code CachedUserTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class CacheWiringTest {

    /** Swap Redis for a heap cache so the test needs no running server. */
    @TestConfiguration
    static class InMemoryCaches {
        @Bean
        @Primary
        CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConfig.USERS_CACHE, CacheConfig.NEARBY_ROUTES_CACHE);
        }
    }

    @Autowired private RouteService routeService;
    @Autowired private CacheManager cacheManager;
    @MockBean private RouteRepository routeRepository;
    @MockBean private RideRepository rideRepository;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames()
                .forEach(n -> cacheManager.getCache(n).clear());
        reset(routeRepository, rideRepository);
        when(rideRepository.findByRouteIn(anyList())).thenReturn(List.of());
    }

    private static Route activeRoute() {
        return Route.builder()
                .id(1L)
                .driver(User.builder().id(9L).phoneNumber("9000000001").build())
                .originLat(21.47).originLng(83.97)
                .destinationLat(21.50).destinationLng(84.01)
                .status("ACTIVE").totalSeats(3).availableSeats(3)
                .build();
    }

    @Test
    @DisplayName("repeated nearby-routes calls hit the database once")
    void nearbyRoutesIsCached() {
        when(routeRepository.findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(activeRoute()));

        for (int i = 0; i < 5; i++) {
            List<RouteResponseDTO> result = routeService.getNearbyRoutes(21.47, 83.97);
            assertThat(result).hasSize(1);
        }

        // The whole point of the cache. If getNearbyRoutes called its @Cacheable
        // body directly instead of through the proxy, this would be 5.
        verify(routeRepository, times(1))
                .findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("GPS jitter within ~11m still hits the same cache entry")
    void coarsensTheCacheKey() {
        when(routeRepository.findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(activeRoute()));

        // A stationary phone reports slightly different coordinates each poll.
        // Keying on the raw value would miss every time and cache nothing.
        routeService.getNearbyRoutes(21.470000, 83.970000);
        routeService.getNearbyRoutes(21.470012, 83.970009);
        routeService.getNearbyRoutes(21.469998, 83.970004);

        verify(routeRepository, times(1))
                .findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("a genuinely different location is a separate entry")
    void differentLocationMissesTheCache() {
        when(routeRepository.findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(activeRoute()));

        routeService.getNearbyRoutes(21.47, 83.97);
        routeService.getNearbyRoutes(22.10, 84.50);

        verify(routeRepository, times(2))
                .findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("evicting clears the region so a new route appears at once")
    void evictionForcesRefresh() {
        when(routeRepository.findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(activeRoute()));

        routeService.getNearbyRoutes(21.47, 83.97);
        routeService.evictNearbyRoutesCache();
        routeService.getNearbyRoutes(21.47, 83.97);

        // Without eviction a rider would not see a newly created route until
        // the TTL lapsed.
        verify(routeRepository, times(2))
                .findActiveRoutesNear(anyDouble(), anyDouble(), anyDouble());
    }
}
