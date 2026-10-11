package com.backend.iniato.repo;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Driver positions in Redis Geo — the live read path for "who is near me".
 *
 * <p>Keeping this query off Postgres is the point. Proximity search is issued
 * constantly by riders, and on Neon's free plan every query spends metered
 * compute and holds the database awake.
 */
@Repository
public class DriverLocationGeoRepository {

    private static final String DRIVER_GEO_KEY = "drivers:geo";

    private final RedisTemplate<String, Object> redisTemplate;

    public DriverLocationGeoRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** A driver and where they are — everything a proximity result needs. */
    public record DriverPosition(Long driverId, double latitude, double longitude,
                                 double distanceMeters) {}

    /**
     * Record a driver's position.
     *
     * <p>Takes the id rather than a {@code User} so the caller on the
     * per-tick hot path has no reason to load the entity.
     */
    public void addDriverLocation(Long driverId, double latitude, double longitude) {
        if (driverId == null) return;
        redisTemplate.opsForGeo()
                .add(DRIVER_GEO_KEY, new Point(longitude, latitude), String.valueOf(driverId));
    }

    /**
     * Drivers within {@code radiusInMeters}, <b>with</b> their coordinates.
     *
     * <p>Redis returns the position alongside each hit, so callers no longer
     * need a per-driver Postgres lookup to recover it — that N+1 was the whole
     * reason this method exists in this shape.
     */
    public List<DriverPosition> findNearbyDriverPositions(
            double longitude, double latitude, double radiusInMeters) {

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = redisTemplate.opsForGeo()
                .search(DRIVER_GEO_KEY,
                        GeoReference.fromCoordinate(new Point(longitude, latitude)),
                        new Distance(radiusInMeters / 1000.0, Metrics.KILOMETERS),
                        args);

        if (results == null) return List.of();

        List<DriverPosition> positions = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results) {
            RedisGeoCommands.GeoLocation<Object> hit = result.getContent();
            Long driverId = parseDriverId(hit.getName());
            if (driverId == null) continue;

            Point point = hit.getPoint();
            double lng = point != null ? point.getX() : 0.0;
            double lat = point != null ? point.getY() : 0.0;
            double distanceMeters = result.getDistance() != null
                    ? result.getDistance().in(Metrics.KILOMETERS).getValue() * 1000.0
                    : 0.0;

            positions.add(new DriverPosition(driverId, lat, lng, distanceMeters));
        }
        return positions;
    }

    /** Ids only, for callers that do not need coordinates. */
    public List<Long> findNearbyDriverIds(double longitude, double latitude, double radiusInMeters) {
        return findNearbyDriverPositions(longitude, latitude, radiusInMeters)
                .stream().map(DriverPosition::driverId).toList();
    }

    public void removeDriverLocation(Long driverId) {
        if (driverId == null) return;
        redisTemplate.opsForGeo().remove(DRIVER_GEO_KEY, String.valueOf(driverId));
    }

    /**
     * Ids of every driver currently in the geo set.
     *
     * <p>Uses ZRANGE rather than the old "radius of 20,000 km from (0,0)"
     * trick, which was both slow and wrong — it missed anyone more than a
     * quarter-circumference from the null island.
     */
    public List<Long> getAllDriverIds() {
        var members = redisTemplate.opsForZSet().range(DRIVER_GEO_KEY, 0, -1);
        if (members == null) return List.of();
        return members.stream().map(this::parseDriverId).filter(java.util.Objects::nonNull).toList();
    }

    private Long parseDriverId(Object raw) {
        if (raw == null) return null;
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException notADriverId) {
            return null;
        }
    }
}
