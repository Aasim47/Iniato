package com.backend.iniato.services.impl;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.dto.RouteResponseDTO;
import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.RideRequest;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.RideRequestRepository;
import com.backend.iniato.repo.RouteRepository;
import com.backend.iniato.repo.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.backend.iniato.services.MatchingService;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MatchingServiceImpl implements MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingServiceImpl.class);

    /** Max angular difference (degrees) between route vector and passenger vector. */
    private static final double MAX_DIRECTION_ANGLE_DEG = 60.0;

    /** Max perpendicular distance (metres) from route corridor. */
    private static final double MAX_CORRIDOR_DEVIATION_METERS = 1500.0;

    private static final double METERS_PER_DEGREE = 111_000.0;

    /** Demand-pool TTL: requests older than this are considered stale. */
    private static final int DEMAND_POOL_TTL_MINUTES = 15;

    /**
     * How long a settled (accepted/cancelled) ride request is kept before the
     * nightly purge removes it. Long enough to investigate a recent dispute,
     * short enough that the table does not grow without bound.
     */
    private static final int SETTLED_REQUEST_RETENTION_DAYS = 30;

    /** Radius to search for pending requests around the route midpoint. */
    private static final double NEARBY_SEARCH_RADIUS_METERS = 5_000.0;

    private static final GeometryFactory GEO_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Match a passenger request to the best available route.
     */
    public Optional<Route> matchPassengerToRoute(RiderCreateRequest request) {
        List<Route> activeRoutes = fetchActiveRoutesNearby(request.pickupLat, request.pickupLng, 5.0);
        double bestScore = Double.MAX_VALUE;
        Route bestRoute = null;
        for (Route route : activeRoutes) {
            if (!isDirectionCompatible(route, request)) continue;
            if (!isPickupAhead(route, request)) continue;
            double etaImpact = calculateEtaImpact(route, request);
            if (route.getAvailableSeats() <= 0) continue;
            double score = scoreRoute(route, etaImpact);
            if (score < bestScore) {
                bestScore = score;
                bestRoute = route;
            }
        }
        if (bestRoute != null) {
            return Optional.of(bestRoute);
        } else {
            addToDemandPool(request);
            return Optional.empty();
        }
    }

    /**
     * Match a route to waiting rider requests (driver-side matching).
     */
    public List<RideRequest> matchRouteToDemand(Route route) {
        List<RideRequest> nearbyRequests = fetchNearbyRiderRequests(route);
        List<RideRequest> compatibleRequests = new ArrayList<>();
        for (RideRequest req : nearbyRequests) {
            if (!isDirectionCompatible(route, req)) continue;
            compatibleRequests.add(req);
        }
        compatibleRequests.sort(Comparator.comparingDouble(req -> calculateDeviation(route, req)));
        return compatibleRequests;
    }

    // -----------------------------------------------------------------------
    // MatchingService interface
    // -----------------------------------------------------------------------

    @Override
    public List<Route> fetchActiveRoutesNearby(double lat, double lng, double radiusKm) {
        // Convert radius in km to approximate degree delta (1° ≈ 111 km)
        double delta = radiusKm / 111.0;
        return routeRepository.findActiveRoutesNear(lat, lng, delta);
    }

    /**
     * Not {@code readOnly}: on a miss, {@link #matchPassengerToRoute} falls
     * through to {@code addToDemandPool}, which saves a new RideRequest. A
     * read-only transaction would flush that write into a Hibernate failure.
     */
    @Override
    @Transactional
    public Optional<RouteResponseDTO> matchPassengerToRouteDto(RiderCreateRequest request) {
        return matchPassengerToRoute(request).map(MatchingServiceImpl::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> fetchActiveRouteDtosNearby(double lat, double lng, double radiusKm) {
        return fetchActiveRoutesNearby(lat, lng, radiusKm).stream()
                .map(MatchingServiceImpl::toDto)
                .toList();
    }

    /**
     * Flatten a Route for the wire.
     *
     * <p>Runs inside the transaction, so touching the lazy {@code driver} here
     * is a normal fetch rather than a lazy-initialisation failure — which is
     * what makes {@code spring.jpa.open-in-view=false} safe on this path. It
     * also keeps the {@code User} (and its password hash) out of the response.
     */
    private static RouteResponseDTO toDto(Route route) {
        return RouteResponseDTO.builder()
                .routeId(route.getId())
                .driverPhone(route.getDriver() != null ? route.getDriver().getPhoneNumber() : null)
                .status(route.getStatus())
                .originLat(route.getOriginLat())
                .originLng(route.getOriginLng())
                .destinationLat(route.getDestinationLat())
                .destinationLng(route.getDestinationLng())
                .originAddress(route.getOriginAddress())
                .destinationAddress(route.getDestinationAddress())
                .vehicleType(route.getVehicleType() != null ? route.getVehicleType().name() : null)
                .totalSeats(route.getTotalSeats())
                .availableSeats(route.getAvailableSeats())
                .completedAt(route.getCompletedAt())
                .earnings(route.getTotalEarnings() != null ? route.getTotalEarnings() : 0.0)
                .build();
    }

    // -----------------------------------------------------------------------
    // Direction helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true when the passenger's travel vector is within
     * {@value MAX_DIRECTION_ANGLE_DEG}° of the route's direction vector AND
     * the pickup is within {@value MAX_CORRIDOR_DEVIATION_METERS} m of the
     * route corridor.
     */
    private boolean isDirectionCompatible(Route route, RiderCreateRequest request) {
        if (route.getOriginLat() == null || route.getDestinationLat() == null) return false;

        double cosLat = Math.cos(Math.toRadians((route.getOriginLat() + route.getDestinationLat()) / 2.0));

        // Route direction vector
        double rVecLat = route.getDestinationLat() - route.getOriginLat();
        double rVecLng = (route.getDestinationLng() - route.getOriginLng()) * cosLat;

        // Passenger direction vector
        double pVecLat = request.dropLat - request.pickupLat;
        double pVecLng = (request.dropLng - request.pickupLng) * cosLat;

        if (!isAngleCompatible(rVecLat, rVecLng, pVecLat, pVecLng)) return false;

        double corridorDev = perpendicularDistanceMeters(
                request.pickupLat, request.pickupLng,
                route.getOriginLat(), route.getOriginLng(),
                route.getDestinationLat(), route.getDestinationLng());

        return corridorDev <= MAX_CORRIDOR_DEVIATION_METERS;
    }

    /**
     * Direction check for RideRequest (pickup/destination are JTS Points).
     */
    private boolean isDirectionCompatible(Route route, RideRequest request) {
        if (route.getOriginLat() == null || route.getDestinationLat() == null) return false;
        if (request.getPickupLocation() == null || request.getDestinationLocation() == null) return false;

        double pPickupLat = request.getPickupLocation().getY();
        double pPickupLng = request.getPickupLocation().getX();
        double pDestLat   = request.getDestinationLocation().getY();
        double pDestLng   = request.getDestinationLocation().getX();

        double cosLat = Math.cos(Math.toRadians((route.getOriginLat() + route.getDestinationLat()) / 2.0));

        double rVecLat = route.getDestinationLat() - route.getOriginLat();
        double rVecLng = (route.getDestinationLng() - route.getOriginLng()) * cosLat;

        double pVecLat = pDestLat - pPickupLat;
        double pVecLng = (pDestLng - pPickupLng) * cosLat;

        if (!isAngleCompatible(rVecLat, rVecLng, pVecLat, pVecLng)) return false;

        double corridorDev = perpendicularDistanceMeters(
                pPickupLat, pPickupLng,
                route.getOriginLat(), route.getOriginLng(),
                route.getDestinationLat(), route.getDestinationLng());

        return corridorDev <= MAX_CORRIDOR_DEVIATION_METERS;
    }

    /**
     * Returns true if the passenger's pickup projects onto the route segment
     * (i.e. t ∈ [-0.1, 1.0]) — the driver is heading toward the pickup,
     * not away from it.
     */
    private boolean isPickupAhead(Route route, RiderCreateRequest request) {
        if (route.getOriginLat() == null || route.getDestinationLat() == null) return false;
        double t = projectionParameter(
                request.pickupLat, request.pickupLng,
                route.getOriginLat(), route.getOriginLng(),
                route.getDestinationLat(), route.getDestinationLng());
        return t >= -0.1 && t <= 1.0;
    }

    // -----------------------------------------------------------------------
    // Geometry utilities
    // -----------------------------------------------------------------------

    private boolean isAngleCompatible(double rVecLat, double rVecLng,
                                       double pVecLat, double pVecLng) {
        double dot  = rVecLat * pVecLat + rVecLng * pVecLng;
        double rMag = Math.sqrt(rVecLat * rVecLat + rVecLng * rVecLng);
        double pMag = Math.sqrt(pVecLat * pVecLat + pVecLng * pVecLng);
        if (rMag < 1e-9 || pMag < 1e-9) return false;
        double cosAngle = Math.max(-1.0, Math.min(1.0, dot / (rMag * pMag)));
        return Math.toDegrees(Math.acos(cosAngle)) <= MAX_DIRECTION_ANGLE_DEG;
    }

    private double perpendicularDistanceMeters(double pLat, double pLng,
                                               double aLat, double aLng,
                                               double bLat, double bLng) {
        double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));
        double ax = aLng * cosLat * METERS_PER_DEGREE;
        double ay = aLat * METERS_PER_DEGREE;
        double bx = bLng * cosLat * METERS_PER_DEGREE;
        double by = bLat * METERS_PER_DEGREE;
        double px = pLng * cosLat * METERS_PER_DEGREE;
        double py = pLat * METERS_PER_DEGREE;
        double abx = bx - ax, aby = by - ay;
        double segLenSq = abx * abx + aby * aby;
        if (segLenSq < 1e-9) {
            double dx = px - ax, dy = py - ay;
            return Math.sqrt(dx * dx + dy * dy);
        }
        return Math.abs(abx * (ay - py) - aby * (ax - px)) / Math.sqrt(segLenSq);
    }

    private double projectionParameter(double pLat, double pLng,
                                       double aLat, double aLng,
                                       double bLat, double bLng) {
        double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));
        double abx = (bLng - aLng) * cosLat, aby = bLat - aLat;
        double apx = (pLng - aLng) * cosLat, apy = pLat - aLat;
        double segLenSq = abx * abx + aby * aby;
        if (segLenSq < 1e-9) return 0.0;
        return (apx * abx + apy * aby) / segLenSq;
    }

    // -----------------------------------------------------------------------
    // ETA impact — Haversine detour estimate
    // -----------------------------------------------------------------------

    /**
     * Estimates how many extra seconds the driver would spend picking up this
     * passenger, using the triangular detour formula:
     *   detour = haversine(origin→pickup) + haversine(pickup→drop) − haversine(origin→drop)
     * Assumes 30 km/h average urban speed.
     */
    private double calculateEtaImpact(Route route, RiderCreateRequest request) {
        if (route.getOriginLat() == null || route.getOriginLng() == null) return 0.0;
        double originToPickup = haversineKm(route.getOriginLat(), route.getOriginLng(),
                                            request.pickupLat, request.pickupLng);
        double pickupToDrop   = haversineKm(request.pickupLat, request.pickupLng,
                                            request.dropLat, request.dropLng);
        double originToDrop   = haversineKm(route.getOriginLat(), route.getOriginLng(),
                                            request.dropLat, request.dropLng);
        double detourKm = Math.max(0.0, originToPickup + pickupToDrop - originToDrop);
        return detourKm / 30.0 * 3600.0; // seconds
    }

    private double scoreRoute(Route route, double etaImpact) {
        // Lower is better: penalise detour, reward spare seats.
        double seatBonus = route.getAvailableSeats() > 0 ? 1.0 / route.getAvailableSeats() : 1000.0;
        return etaImpact + seatBonus * 60.0;
    }

    // -----------------------------------------------------------------------
    // Demand pool — persist unmatched requests for driver-side polling
    // -----------------------------------------------------------------------

    /**
     * Saves the unmatched passenger request to the DB demand pool (status=PENDING)
     * so drivers can later poll nearby waiting riders.
     */
    private void addToDemandPool(RiderCreateRequest request) {
        try {
            Long userId = Long.parseLong(request.riderId);
            User passenger = userRepository.findById(userId).orElse(null);
            if (passenger == null) {
                log.warn("addToDemandPool: unknown riderId={}", request.riderId);
                return;
            }
            Point pickup = GEO_FACTORY.createPoint(
                    new Coordinate(request.pickupLng, request.pickupLat));
            pickup.setSRID(4326);
            Point destination = GEO_FACTORY.createPoint(
                    new Coordinate(request.dropLng, request.dropLat));
            destination.setSRID(4326);

            RideRequest poolEntry = RideRequest.builder()
                    .passenger(passenger)
                    .pickupLocation(pickup)
                    .destinationLocation(destination)
                    .pickupTime(LocalDateTime.now())
                    .status("PENDING")
                    .build();
            rideRequestRepository.save(poolEntry);
            log.debug("Added riderId={} to demand pool", request.riderId);
        } catch (NumberFormatException e) {
            log.warn("addToDemandPool: riderId '{}' is not a valid Long", request.riderId);
        }
    }

    // -----------------------------------------------------------------------
    // Nearby rider requests — PostGIS spatial query
    // -----------------------------------------------------------------------

    /**
     * Fetches PENDING ride requests within {@value NEARBY_SEARCH_RADIUS_METERS} m
     * of the route midpoint that were created within the last
     * {@value DEMAND_POOL_TTL_MINUTES} minutes.
     */
    private List<RideRequest> fetchNearbyRiderRequests(Route route) {
        if (route.getOriginLat() == null || route.getDestinationLat() == null) {
            return Collections.emptyList();
        }
        double midLat = (route.getOriginLat() + route.getDestinationLat()) / 2.0;
        double midLng = (route.getOriginLng() + route.getDestinationLng()) / 2.0;
        LocalDateTime since = LocalDateTime.now().minusMinutes(DEMAND_POOL_TTL_MINUTES);
        return rideRequestRepository.findPendingNear(midLat, midLng,
                                                     NEARBY_SEARCH_RADIUS_METERS, since);
    }

    // -----------------------------------------------------------------------
    // Scheduled cleanup — remove stale PENDING requests
    // -----------------------------------------------------------------------

    /**
     * Purge demand-pool entries older than {@value DEMAND_POOL_TTL_MINUTES} min.
     *
     * <p>Runs every 15 minutes rather than every 5. The TTL is 15 minutes, so
     * polling three times per TTL only found something to delete on one run in
     * three — the other two were a query that woke the database for nothing.
     * With Neon's scale-to-zero, a pointless query every 5 minutes is enough on
     * its own to stop the compute ever suspending.
     *
     * <p>Offset from the hour so it does not land with every other scheduled
     * job in the fleet.
     */
    @Scheduled(fixedRate = 900_000, initialDelay = 120_000)
    @Transactional
    public void purgeExpiredDemandPool() {
        LocalDateTime expiry = LocalDateTime.now().minusMinutes(DEMAND_POOL_TTL_MINUTES);
        int deleted = rideRequestRepository.deleteExpiredPendingRequests(expiry);
        if (deleted > 0) {
            log.info("Purged {} expired demand-pool requests", deleted);
        }
    }

    /**
     * Delete settled ride requests older than {@value SETTLED_REQUEST_RETENTION_DAYS} days.
     *
     * <p>The PENDING sweep above never touched these: requests raised through
     * {@code RideService.requestSharedRide} are REQUESTED and end ACCEPTED or
     * CANCELLED, so they simply accumulated — one row per join attempt, each
     * holding two PostGIS geometries, against a 0.5 GB free-tier cap.
     *
     * <p>Runs daily at 03:40 local time: off-peak, and off the hour so it does
     * not coincide with everyone else's midnight jobs. Anything still
     * referenced by a {@code ride_passengers} row is left alone.
     */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void purgeSettledRideRequests() {
        LocalDateTime expiry = LocalDateTime.now().minusDays(SETTLED_REQUEST_RETENTION_DAYS);
        int deleted = rideRequestRepository.deleteSettledRequestsBefore(expiry);
        if (deleted > 0) {
            log.info("Purged {} settled ride requests older than {} days",
                    deleted, SETTLED_REQUEST_RETENTION_DAYS);
        }
    }

    // -----------------------------------------------------------------------
    // Geometry utilities (unchanged)
    // -----------------------------------------------------------------------

    private double calculateDeviation(Route route, RideRequest request) {
        if (request.getPickupLocation() == null) return Double.MAX_VALUE;
        return perpendicularDistanceMeters(
                request.getPickupLocation().getY(), request.getPickupLocation().getX(),
                route.getOriginLat(), route.getOriginLng(),
                route.getDestinationLat(), route.getDestinationLng());
    }

    /** Haversine great-circle distance in kilometres. */
    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}


