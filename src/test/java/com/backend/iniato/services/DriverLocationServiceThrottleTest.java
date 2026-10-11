package com.backend.iniato.services;

import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.entity.DriverLocation;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.DriverLocationGeoRepository;
import com.backend.iniato.repo.DriverLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write throttle on the location hot path.
 *
 * <p>This is the single biggest source of database traffic in the app — a tick
 * per driver every few seconds, forever. On Neon's free plan that traffic both
 * burns metered compute and stops the database ever autosuspending, so these
 * assertions are guarding a cost guarantee, not just a performance one.
 */
class DriverLocationServiceThrottleTest {

    private DriverLocationRepository locationRepository;
    private DriverLocationGeoRepository geoRepository;
    private SimpMessagingTemplate messagingTemplate;
    private UserLookupService userLookupService;
    private DriverLocationService service;

    @BeforeEach
    void setUp() {
        locationRepository = mock(DriverLocationRepository.class);
        geoRepository = mock(DriverLocationGeoRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        userLookupService = mock(UserLookupService.class);

        User driver = User.builder().id(1L).phoneNumber("9000000001").build();
        when(userLookupService.loadManaged(1L)).thenReturn(driver);
        when(locationRepository.findByDriverId(anyLong())).thenReturn(Optional.empty());

        service = new DriverLocationService(
                locationRepository, geoRepository, messagingTemplate, userLookupService);
    }

    private static DriverLocationUpdateDTO tick(long driverId, Long rideId) {
        DriverLocationUpdateDTO dto = new DriverLocationUpdateDTO();
        dto.setDriverId(driverId);
        dto.setLatitude(21.47);
        dto.setLongitude(83.97);
        dto.setRideId(rideId);
        return dto;
    }

    @Test
    @DisplayName("a burst of ticks produces exactly one database write")
    void throttlesRepeatedTicks() {
        for (int i = 0; i < 50; i++) {
            service.updateDriverLocation(tick(1L, 99L));
        }

        // The whole point: 50 ticks, 1 write.
        verify(locationRepository, times(1)).save(any(DriverLocation.class));
    }

    @Test
    @DisplayName("every tick still reaches Redis and the passengers")
    void alwaysUpdatesRedisAndBroadcasts() {
        for (int i = 0; i < 50; i++) {
            service.updateDriverLocation(tick(1L, 99L));
        }

        // Throttling must never degrade live tracking — Redis holds the live
        // position and the broadcast is what passengers actually see.
        verify(geoRepository, times(50)).addDriverLocation(1L, 21.47, 83.97);
        verify(messagingTemplate, times(50)).convertAndSend(eqTopic("/topic/ride/99"), any(Object.class));
    }

    @Test
    @DisplayName("each driver is throttled independently")
    void throttlesPerDriver() {
        User second = User.builder().id(2L).phoneNumber("9000000002").build();
        when(userLookupService.loadManaged(2L)).thenReturn(second);

        service.updateDriverLocation(tick(1L, null));
        service.updateDriverLocation(tick(2L, null));
        service.updateDriverLocation(tick(1L, null));
        service.updateDriverLocation(tick(2L, null));

        // One write each, not one between them.
        verify(locationRepository, times(2)).save(any(DriverLocation.class));
    }

    @Test
    @DisplayName("a failed write is retried on the next tick, not swallowed for a minute")
    void retriesAfterFailure() {
        when(locationRepository.save(any(DriverLocation.class)))
                .thenThrow(new RuntimeException("connection reset"))
                .thenReturn(null);

        // Must not propagate: a backup write failing cannot break live tracking.
        service.updateDriverLocation(tick(1L, null));
        service.updateDriverLocation(tick(1L, null));

        // The throttle slot was released on failure, so the second tick retries
        // instead of waiting out the full interval.
        verify(locationRepository, times(2)).save(any(DriverLocation.class));
    }

    @Test
    @DisplayName("concurrent ticks for one driver cannot both claim the write slot")
    void throttleIsAtomicUnderConcurrency() throws Exception {
        final int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.updateDriverLocation(tick(1L, null));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).isZero();

        // merge() claims the slot atomically, so a simultaneous burst still
        // writes once — a read-then-write check would let several through.
        verify(locationRepository, times(1)).save(any(DriverLocation.class));
    }

    @Test
    @DisplayName("going offline writes through and clears the throttle")
    void offlineBypassesThrottle() {
        User driver = User.builder().id(1L).phoneNumber("9000000001").build();
        DriverLocation existing = DriverLocation.builder().driver(driver).build();
        when(locationRepository.findByDriverId(1L)).thenReturn(Optional.of(existing));

        service.updateDriverLocation(tick(1L, null));   // claims the slot
        service.removeDriverLocation(driver);           // must not be throttled

        verify(geoRepository).removeDriverLocation(1L);
        assertThat(existing.isOnline()).isFalse();

        // Throttle was cleared, so the next tick writes immediately rather than
        // leaving the driver marked offline for a minute while they are moving.
        service.updateDriverLocation(tick(1L, null));
        verify(locationRepository, times(3)).save(any(DriverLocation.class));
    }

    @Test
    @DisplayName("a tick with no ride id updates position but broadcasts nothing")
    void noBroadcastWithoutRide() {
        service.updateDriverLocation(tick(1L, null));

        verify(geoRepository).addDriverLocation(1L, 21.47, 83.97);
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("a malformed tick is ignored rather than throwing")
    void ignoresTickWithoutDriverId() {
        DriverLocationUpdateDTO noDriverId = new DriverLocationUpdateDTO();
        noDriverId.setLatitude(21.47);
        noDriverId.setLongitude(83.97);

        service.updateDriverLocation(null);
        service.updateDriverLocation(noDriverId);

        verify(geoRepository, never()).addDriverLocation(anyLong(), anyDouble(), anyDouble());
        verify(locationRepository, never()).save(any(DriverLocation.class));
    }

    private static String eqTopic(String topic) {
        return org.mockito.ArgumentMatchers.eq(topic);
    }
}
