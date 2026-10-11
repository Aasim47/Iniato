package com.backend.iniato.services;

import com.backend.iniato.dto.DriverLocationBroadcastDTO;
import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.entity.DriverLocation;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.DriverLocationGeoRepository;
import com.backend.iniato.repo.DriverLocationRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where drivers are right now.
 *
 * <p><b>Redis is the live source of truth; Postgres is a slow backup.</b> This
 * method runs on every WebSocket location tick — several times a second per
 * online driver — and it used to do three Postgres round trips each time (load
 * the User, load the DriverLocation, update it). Ten drivers pinging every five
 * seconds was six queries a second, forever, for data that was already being
 * written to Redis Geo on the very next line.
 *
 * <p>On Neon's free plan that is doubly expensive: the queries burn CU-hours,
 * and a continuous write stream means the compute can never autosuspend, so the
 * 100 CU-hour monthly budget drains around the clock whether or not anyone is
 * riding.
 *
 * <p>Two changes fix it:
 * <ol>
 *   <li>The broadcast and the Redis write happen first and always. Nothing on
 *       the live path waits for Postgres.</li>
 *   <li>Postgres is written at most once per {@value #DB_PERSIST_INTERVAL_MS} ms
 *       per driver. Roughly a 98% cut in writes, and long idle gaps now exist
 *       for Neon to sleep in.</li>
 * </ol>
 *
 * <p><b>What the throttle costs.</b> {@code driver_locations} can lag reality by
 * up to a minute. Nothing user-facing reads it: live tracking goes over the
 * WebSocket and proximity search goes to Redis Geo. The row is a cold-start
 * fallback for when Redis is empty, and a minute-stale position is fine for
 * that. {@code RideMatchingService} still queries the table directly with
 * PostGIS and inherits the same lag.
 */
@Service
public class DriverLocationService {

    private static final Logger log = LoggerFactory.getLogger(DriverLocationService.class);

    private static final int SRID = 4326;

    /**
     * Minimum gap between Postgres writes for one driver. Sized so a driver
     * moving at speed still leaves a reasonably fresh row, while a stationary
     * fleet generates almost no write traffic.
     */
    private static final long DB_PERSIST_INTERVAL_MS = 60_000L;

    private final DriverLocationRepository driverLocationRepository;
    private final DriverLocationGeoRepository driverLocationGeoRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserLookupService userLookupService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    /**
     * driverId → epoch ms of that driver's last Postgres write.
     *
     * <p>In-heap and per-instance on purpose: it is a rate limiter, not state.
     * Losing it on restart costs one extra write per driver. With several
     * instances each throttles independently, which is still a ~98% cut.
     *
     * <p>Bounded by {@link #forgetDriver}, called when a driver goes offline,
     * so this cannot grow without limit.
     */
    private final Map<Long, Long> lastPersistedAtMs = new ConcurrentHashMap<>();

    @Autowired
    public DriverLocationService(DriverLocationRepository driverLocationRepository,
                                 DriverLocationGeoRepository driverLocationGeoRepository,
                                 SimpMessagingTemplate messagingTemplate,
                                 UserLookupService userLookupService) {
        this.driverLocationRepository = driverLocationRepository;
        this.driverLocationGeoRepository = driverLocationGeoRepository;
        this.messagingTemplate = messagingTemplate;
        this.userLookupService = userLookupService;
    }

    /**
     * Handle one location tick.
     *
     * <p>Order matters: Redis and the WebSocket broadcast come first so live
     * tracking never waits on — or is broken by — the database.
     */
    public void updateDriverLocation(DriverLocationUpdateDTO dto) {
        if (dto == null || dto.getDriverId() == null) return;
        final Long driverId = dto.getDriverId();

        // 1. Redis Geo — the live position readers actually use. No DB touch:
        //    the geo set is keyed by driver id, so no User load is needed.
        driverLocationGeoRepository.addDriverLocation(
                driverId, dto.getLatitude(), dto.getLongitude());

        // 2. Broadcast to passengers tracking this ride.
        //
        //    Published to the "/location" sub-topic, which is where the rider app
        //    actually listens (Iniato_fe websocket_service.dart, subscribeToRide:
        //    "/topic/ride/{id}/location"). This previously went to the bare
        //    "/topic/ride/{id}", so the driver's live position never reached the
        //    rider's map: the rider's bare-topic handler reads a "type" field and
        //    drops any message without one, and this DTO has no "type".
        //
        //    The bare topic carries status events (PASSENGER_ADDED, RIDE_STARTED,
        //    RIDE_COMPLETED), all of which are typed; location is a separate,
        //    high-frequency stream and belongs on its own topic. This now matches
        //    NotificationService.broadcastLocationUpdate, which the REST path uses.
        if (dto.getRideId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/ride/" + dto.getRideId() + "/location",
                    new DriverLocationBroadcastDTO(
                            driverId,
                            dto.getLatitude(),
                            dto.getLongitude(),
                            LocalDateTime.now().toString()));
        }

        // 3. Postgres, at most once a minute per driver.
        if (shouldPersist(driverId)) {
            try {
                persistLocation(driverId, dto.getLatitude(), dto.getLongitude());
            } catch (Exception e) {
                // A failed backup write must not break live tracking. Roll the
                // timestamp back so the next tick retries instead of waiting
                // out a full interval.
                lastPersistedAtMs.remove(driverId);
                log.warn("Could not persist location for driver {}: {}", driverId, e.getMessage());
            }
        }
    }

    /**
     * True at most once per {@link #DB_PERSIST_INTERVAL_MS} per driver.
     *
     * <p>Claims the slot atomically, so concurrent ticks from the same driver
     * cannot both decide to write.
     */
    private boolean shouldPersist(Long driverId) {
        final long now = System.currentTimeMillis();
        final boolean[] claimed = {false};

        // compute() runs the remapping function atomically under the bin lock,
        // so exactly one concurrent caller can set the flag.
        //
        // The flag is set inside the function rather than inferred from the
        // returned value. Comparing the return against `now` looks equivalent
        // but is not: two ticks in the same millisecond produce an unchanged
        // `previous` that is numerically equal to `now`, so the loser reads as
        // a winner and writes too. (Comparing boxed Longs with == fails for the
        // opposite reason — Long caches only -128..127, so every real
        // epoch-millis value compares false and nothing is ever throttled.)
        lastPersistedAtMs.compute(driverId, (id, previous) -> {
            if (previous == null || now - previous >= DB_PERSIST_INTERVAL_MS) {
                claimed[0] = true;
                return now;
            }
            return previous;
        });
        return claimed[0];
    }

    /**
     * Write the driver's position to Postgres.
     *
     * <p>The User is resolved through {@link UserLookupService#loadManaged} only
     * when a row has to be created; an existing row is updated without loading
     * the driver at all, so the common case is one select plus one update.
     *
     * <p>Deliberately <b>not</b> {@code @Transactional}: this is called from
     * {@link #updateDriverLocation} in the same class, and Spring's proxy does
     * not intercept self-invocation — the annotation would read as a guarantee
     * it could not provide. {@code save()} carries its own transaction, which
     * is all a single-row upsert needs.
     */
    private void persistLocation(Long driverId, double latitude, double longitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID);

        DriverLocation location = driverLocationRepository.findByDriverId(driverId)
                .orElseGet(() -> DriverLocation.builder()
                        .driver(userLookupService.loadManaged(driverId))
                        .build());

        location.setCurrentLocation(point);
        location.setOnline(true);
        location.setLastUpdated(LocalDateTime.now());
        driverLocationRepository.save(location);
    }

    /**
     * Driver has gone offline: drop them from Redis and clear the throttle.
     *
     * <p>The Postgres {@code online} flag is updated too, since going offline is
     * rare and must not be lost to throttling — a driver left showing as online
     * would keep appearing in fallback searches.
     */
    @Transactional
    public void removeDriverLocation(User driver) {
        if (driver == null || driver.getId() == null) return;
        driverLocationGeoRepository.removeDriverLocation(driver.getId());
        forgetDriver(driver.getId());

        driverLocationRepository.findByDriverId(driver.getId()).ifPresent(location -> {
            location.setOnline(false);
            location.setLastUpdated(LocalDateTime.now());
            driverLocationRepository.save(location);
        });
    }

    /** Stop tracking a driver's write cadence. Keeps the throttle map bounded. */
    public void forgetDriver(Long driverId) {
        lastPersistedAtMs.remove(driverId);
    }

    /**
     * Drivers within {@code radiusInMeters}, served entirely from Redis.
     *
     * <p>Previously this read ids from Redis and then issued one Postgres query
     * <em>per driver</em> just to recover coordinates Redis already held — a
     * textbook N+1 on a request path. Redis returns positions with the results,
     * so the database is not involved.
     */
    public List<DriverLocationUpdateDTO> findNearbyDrivers(
            double longitude, double latitude, double radiusInMeters) {

        List<DriverLocationGeoRepository.DriverPosition> positions =
                driverLocationGeoRepository.findNearbyDriverPositions(
                        longitude, latitude, radiusInMeters);

        List<DriverLocationUpdateDTO> nearby = new ArrayList<>(positions.size());
        for (DriverLocationGeoRepository.DriverPosition position : positions) {
            DriverLocationUpdateDTO dto = new DriverLocationUpdateDTO();
            dto.setDriverId(position.driverId());
            dto.setLatitude(position.latitude());
            dto.setLongitude(position.longitude());
            nearby.add(dto);
        }
        return nearby;
    }
}
