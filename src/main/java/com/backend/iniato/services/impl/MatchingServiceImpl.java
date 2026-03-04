package com.backend.iniato.services.impl;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.RideRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.backend.iniato.services.MatchingService;

import java.util.*;

@Service
public class MatchingServiceImpl implements MatchingService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Match a passenger request to the best available route.
     * @param request RiderCreateRequest
     * @return Optional<Route> best route or empty if none
     */
    public Optional<Route> matchPassengerToRoute(RiderCreateRequest request) {
        // 1. Fetch active routes within X km radius (Geo query via Redis or DB)
        List<Route> activeRoutes = fetchActiveRoutesNearby(request.pickupLat, request.pickupLng, 5.0);
        double bestScore = Double.MAX_VALUE;
        Route bestRoute = null;
        for (Route route : activeRoutes) {
            // 2. Check direction compatibility
            if (!isDirectionCompatible(route, request)) continue;
            // 3. Check pickup is ahead of driver, no backtracking
            if (!isPickupAhead(route, request)) continue;
            // 4. Calculate ETA impact
            double etaImpact = calculateEtaImpact(route, request);
            // 5. Check seat availability
            if (route.getAvailableSeats() <= 0) continue;
            // 6. Score route
            double score = scoreRoute(route, etaImpact);
            if (score < bestScore) {
                bestScore = score;
                bestRoute = route;
            }
        }
        // 7. Return best route or add to demand pool
        if (bestRoute != null) {
            return Optional.of(bestRoute);
        } else {
            addToDemandPool(request);
            return Optional.empty();
        }
    }

    /**
     * Match a route to waiting rider requests (driver-side matching).
     * @param route Route
     * @return List<RideRequest> sorted by minimal deviation
     */
    public List<RideRequest> matchRouteToDemand(Route route) {
        // 1. Fetch rider requests near route corridor (Redis GEO or DB)
        List<RideRequest> nearbyRequests = fetchNearbyRiderRequests(route);
        List<RideRequest> compatibleRequests = new ArrayList<>();
        for (RideRequest req : nearbyRequests) {
            // 2. Filter by direction compatibility
            if (!isDirectionCompatible(route, req)) continue;
            compatibleRequests.add(req);
        }
        // 3. Sort by minimal deviation
        compatibleRequests.sort(Comparator.comparingDouble(req -> calculateDeviation(route, req)));
        return compatibleRequests;
    }

    // --- Helper methods (stubs, to be implemented) ---
    @Override
    public List<Route> fetchActiveRoutesNearby(double lat, double lng, double radiusKm) {
        // Stub: return empty list for now
        return new ArrayList<>();
    }
    private boolean isDirectionCompatible(Route route, RiderCreateRequest request) {
        // TODO: Implement direction check
        return true;
    }
    private boolean isDirectionCompatible(Route route, RideRequest request) {
        // TODO: Implement direction check
        return true;
    }
    private boolean isPickupAhead(Route route, RiderCreateRequest request) {
        // TODO: Implement pickup ahead logic
        return true;
    }
    private double calculateEtaImpact(Route route, RiderCreateRequest request) {
        // TODO: Use Maps API or estimate
        return 0.0;
    }
    private double scoreRoute(Route route, double etaImpact) {
        // TODO: Implement scoring logic
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
        // TODO: Calculate route deviation for pickup/drop
        return 0.0;
    }
}
