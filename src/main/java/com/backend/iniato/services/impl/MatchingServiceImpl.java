package com.backend.iniato.services.impl;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.RideRequest;
import com.backend.iniato.repo.RouteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.backend.iniato.services.MatchingService;

import java.util.*;

@Service
public class MatchingServiceImpl implements MatchingService {

    /** Max angular difference (degrees) between route vector and passenger vector. */
    private static final double MAX_DIRECTION_ANGLE_DEG = 60.0;

    /** Max perpendicular distance (metres) from route corridor. */
    private static final double MAX_CORRIDOR_DEVIATION_METERS = 1500.0;

    private static final double METERS_PER_DEGREE = 111_000.0;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RouteRepository routeRepository;

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
    // Remaining helpers (stubs retained)
    // -----------------------------------------------------------------------

    private double calculateEtaImpact(Route route, RiderCreateRequest request) {
        // TODO: Use Maps API or Haversine estimate
        return 0.0;
    }

    private double scoreRoute(Route route, double etaImpact) {
        return etaImpact;
    }

    private void addToDemandPool(RiderCreateRequest request) {
        // TODO: Store request in Redis demand pool
    }

    private List<RideRequest> fetchNearbyRiderRequests(Route route) {
        // TODO: Use Redis GEO or DB query
        return new ArrayList<>();
    }

    private double calculateDeviation(Route route, RideRequest request) {
        if (request.getPickupLocation() == null) return Double.MAX_VALUE;
        return perpendicularDistanceMeters(
                request.getPickupLocation().getY(), request.getPickupLocation().getX(),
                route.getOriginLat(), route.getOriginLng(),
                route.getDestinationLat(), route.getDestinationLng());
    }
}


