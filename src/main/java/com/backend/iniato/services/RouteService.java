package com.backend.iniato.services;

import com.backend.iniato.config.CacheConfig;
import com.backend.iniato.dto.*;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.enums.VehicleType;
import com.backend.iniato.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RouteService {

    /**
     * Decimal places kept in the nearby-routes cache key. Five places is about
     * 1.1 m, four is about 11 m — fine enough that the cached answer is still
     * centred on the rider, coarse enough that GPS jitter does not produce a
     * fresh key (and a fresh query) on every poll.
     */
    private static final int CACHE_KEY_PRECISION = 4;

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RideRepository rideRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final NotificationService notificationService;
    private final FareCalculationService fareCalculationService;
    private final RideSettlementService rideSettlementService;
    private final DriverDuesService driverDuesService;
    private final ApplicationContext applicationContext;

    @Autowired
    public RouteService(RouteRepository routeRepository,
                        RouteStopRepository routeStopRepository,
                        RideRepository rideRepository,
                        RidePassengerRepository ridePassengerRepository,
                        DriverProfileRepository driverProfileRepository,
                        NotificationService notificationService,
                        FareCalculationService fareCalculationService,
                        RideSettlementService rideSettlementService,
                        DriverDuesService driverDuesService,
                        ApplicationContext applicationContext) {
        this.rideSettlementService = rideSettlementService;
        this.driverDuesService = driverDuesService;
        this.applicationContext = applicationContext;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.rideRepository = rideRepository;
        this.ridePassengerRepository = ridePassengerRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.notificationService = notificationService;
        this.fareCalculationService = fareCalculationService;
    }

    /**
     * Driver declares a new route. Creates Route + linked Ride, broadcasts to nearby riders.
     * <p>
     * Seat count is derived from the driver's registered vehicle, never from
     * {@code request.totalSeats} — a driver cannot advertise more seats than
     * their vehicle physically has.
     */
    @Transactional
    public RouteResponseDTO createRoute(RouteCreateRequest request, User driver) {
        // Declaring a route is taking on new work — blocked while dues are unpaid.
        driverDuesService.assertCanDrive(driver);

        VehicleType vehicleType = driverProfileRepository.findByUser(driver)
                .map(DriverProfile::getVehicleType)
                .orElseThrow(() -> new IllegalStateException(
                        "Set up your driver profile and vehicle type before creating a route."));

        int capacity = vehicleType.getCapacity();

        Route route = Route.builder()
                .driver(driver)
                .originLat(request.originLat)
                .originLng(request.originLng)
                .destinationLat(request.destinationLat)
                .destinationLng(request.destinationLng)
                .originAddress(request.originAddress)
                .destinationAddress(request.destinationAddress)
                .vehicleType(vehicleType)
                .totalSeats(capacity)
                .availableSeats(capacity)
                .status("ACTIVE")
                .startTime(LocalDateTime.now())
                .build();
        routeRepository.save(route);

        Ride ride = Ride.builder()
                .driver(driver)
                .route(route)
                .pickupLocation(request.originAddress)
                .destination(request.destinationAddress)
                .requestedTime(LocalDateTime.now())
                .status(RideStatus.POOL_FORMING)
                .build();
        rideRepository.save(ride);

        RouteResponseDTO dto = toResponse(route, ride.getId());
        // A new bookable route must appear in discovery immediately, not after
        // the cache TTL lapses.
        evictNearbyRoutesAfterCommit();
        notificationService.broadcastNewRoute(dto);
        return dto;
    }

    /**
     * Find active routes within ~800 m of the rider's location.
     *
     * <p><b>Cached for 8 seconds, on a coarsened location.</b> This is a polling
     * endpoint: every rider with the discovery screen open hits it repeatedly,
     * and each call costs a PostGIS scan plus a batched ride lookup. Riders in
     * the same area would otherwise each pay for the same answer.
     *
     * <p>Keying on the raw lat/lng would defeat the cache — GPS jitters in the
     * sixth decimal place, so no two calls would ever share a key. The key is
     * rounded to ~11 m ({@link #CACHE_KEY_PRECISION} decimal places), which is
     * far finer than the 800 m search radius, so the result a rider gets is
     * still centred essentially on them.
     *
     * <p>The 8-second TTL is the staleness budget. These DTOs carry live
     * {@code availableSeats}, which is exactly why the old {@code @Cacheable}
     * was removed — but the danger there was an unbounded TTL with no eviction.
     * Eight seconds is shorter than the time it takes a rider to read the list
     * and tap, and a stale seat is caught anyway: joining re-checks seats inside
     * a locked transaction ({@code RideService.requestSharedRide}), so the worst
     * case is a clear "no seats available", never an overbooking.
     */
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getNearbyRoutes(double lat, double lng) {
        return self().getNearbyRoutesCached(
                round(lat, CACHE_KEY_PRECISION),
                round(lng, CACHE_KEY_PRECISION));
    }

    /**
     * The cached body of {@link #getNearbyRoutes}.
     *
     * <p>Public, and invoked through {@link #self()}, because Spring's caching
     * is proxy-based: a plain internal call would bypass the proxy and the
     * cache would silently never be consulted.
     */
    @Cacheable(value = CacheConfig.NEARBY_ROUTES_CACHE, key = "#lat + ':' + #lng")
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getNearbyRoutesCached(double lat, double lng) {
        double delta = 0.007;
        final List<Route> routes = routeRepository.findActiveRoutesNear(lat, lng, delta);
        final Map<Long, Long> rideIdByRoute = rideIdsByRoute(routes);
        return routes.stream()
                .map(r -> toResponse(r, rideIdByRoute.get(r.getId())))
                .collect(Collectors.toList());
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /** One batched query mapping routeId → rideId, replacing per-route lookups. */
    private Map<Long, Long> rideIdsByRoute(List<Route> routes) {
        if (routes.isEmpty()) return Map.of();
        return rideRepository.findByRouteIn(routes).stream()
                .filter(rd -> rd.getRoute() != null)
                .collect(Collectors.toMap(rd -> rd.getRoute().getId(), Ride::getId, (a, b) -> a));
    }

    /** Driver updates their current location — broadcasts to passengers on the active ride. */
    @Transactional(readOnly = true)
    public void updateLocation(Long routeId, RouteUpdateLocationRequest request, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        if (!route.getDriver().getId().equals(driver.getId()))
            throw new SecurityException("Unauthorized: not your route");
        rideRepository.findByRoute(route).ifPresent(ride -> {
            // Same topic AND same payload shape as the WebSocket path in
            // DriverLocationService. The two disagreed: this one sent
            // {driverId:"7", lat, lng} while the rider app parses
            // {driverId:7, latitude, longitude} — so every field came through as
            // 0 and the marker sat at the equator. DriverLocationBroadcastDTO is
            // the shape the client actually reads.
            notificationService.broadcastLocationUpdate(
                    ride.getId(),
                    new DriverLocationBroadcastDTO(
                            driver.getId(),
                            request.lat,
                            request.lng,
                            LocalDateTime.now().toString()));
        });
    }

    /** Returns all routes (active + completed) for the current driver. */
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getMyRoutes(User driver) {
        final List<Route> routes = routeRepository.findByDriver(driver);
        final Map<Long, Long> rideIdByRoute = rideIdsByRoute(routes);
        return routes.stream()
                .map(r -> toResponse(r, rideIdByRoute.get(r.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Driver cancels a route before it starts.
     * Broadcasts ROUTE_CANCELLED so waiting riders see the card removed.
     */
    @Transactional
    public void cancelRoute(Long routeId, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        if (!route.getDriver().getId().equals(driver.getId()))
            throw new SecurityException("Unauthorized: not your route");

        LocalDateTime now = LocalDateTime.now();
        route.setStatus("CANCELLED");
        route.setEndTime(now);
        route.setCompletedAt(now);
        
        try {
            routeRepository.save(route);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to cancel route due to concurrent modification", e);
        }

        rideRepository.findByRoute(route).ifPresent(ride -> {
            ride.setStatus(RideStatus.CANCELLED);
            rideRepository.save(ride);
            // Mark pending/confirmed passengers as LEFT
            ridePassengerRepository.findByRide(ride).forEach(rp -> {
                if (rp.getStatus() == RidePassengerStatus.PENDING
                        || rp.getStatus() == RidePassengerStatus.CONFIRMED) {
                    rp.setStatus(RidePassengerStatus.LEFT);
                    ridePassengerRepository.save(rp);
                }
            });
            RideCancelledEvent event = new RideCancelledEvent();
            event.rideId = String.valueOf(ride.getId());
            event.reason = "Driver cancelled the route";
            notificationService.notifyDriverRideCancelled(driver.getId(), event);
        });

        // A cancelled route must stop appearing in discovery at once.
        evictNearbyRoutesAfterCommit();

        // Remove the card from RouteMatchScreen for all waiting riders
        notificationService.broadcastRouteUpdated(toResponse(route, null));
    }

    /**
     * Driver marks route as completed.
     * <ul>
     *   <li>Sets completedAt / endTime on Route.</li>
     *   <li>Calculates each CONFIRMED passenger's fare using their own A→B distance.</li>
     *   <li>Adds in DROPPED passengers' fareShares for total driver earnings.</li>
     *   <li>Sends per-passenger RIDE_COMPLETED WebSocket events.</li>
     * </ul>
     */
    @Transactional
    public RouteResponseDTO completeRoute(Long routeId, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        if (!route.getDriver().getId().equals(driver.getId()))
            throw new SecurityException("Unauthorized: not your route");

        // A repeated PATCH must not re-run the fare loop or re-bill the driver.
        if ("COMPLETED".equals(route.getStatus())) {
            Long completedRideId = rideRepository.findByRoute(route)
                    .map(Ride::getId).orElse(null);
            return toResponse(route, completedRideId);
        }

        LocalDateTime now = LocalDateTime.now();
        route.setStatus("COMPLETED");
        route.setEndTime(now);
        route.setCompletedAt(now);

        Ride ride = rideRepository.findByRoute(route).orElse(null);
        double totalEarnings = 0.0;

        if (ride != null) {
            ride.setEndTime(now);
            ride.setStatus(RideStatus.COMPLETED);
            rideRepository.save(ride);

            List<RidePassenger> confirmed = ridePassengerRepository
                    .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED);
            List<RideCompletedEvent> events = new ArrayList<>();

            for (RidePassenger rp : confirmed) {
                rp.setStatus(RidePassengerStatus.COMPLETED);
                double fare = 0.0, distKm = 0.0;

                // Use passenger's own A→B segment, not the full route A→C
                if (rp.getPickupLocation() != null && rp.getDestinationLocation() != null) {
                    FareEstimateRequestDTO req = new FareEstimateRequestDTO();
                    req.setPickupLat(rp.getPickupLocation().getY());
                    req.setPickupLng(rp.getPickupLocation().getX());
                    req.setDestLat(rp.getDestinationLocation().getY());
                    req.setDestLng(rp.getDestinationLocation().getX());
                    req.setPassengers(1);
                    req.setVehicleType(route.getVehicleType() != null
                            ? route.getVehicleType().name() : null);
                    FareEstimateResponseDTO result = fareCalculationService.calculateSharedFare(req);
                    fare = result.getPerPassengerFare();
                    distKm = result.getDistanceKm();
                }

                // Provisional: settlement below replaces this with the
                // distance-weighted share the rider is actually charged.
                rp.setFareShare(fare);

                RideCompletedEvent event = new RideCompletedEvent();
                event.rideId = String.valueOf(ride.getId());
                event.passengerPhone = rp.getPassenger().getPhoneNumber();
                event.passengerEmail = rp.getPassenger().getEmail() != null
                        ? rp.getPassenger().getEmail() : rp.getPassenger().getPhoneNumber();
                event.fareAmount = fare;
                event.distanceKm = distKm;
                events.add(event);
            }
            ridePassengerRepository.saveAll(confirmed);

            // â”€â”€ Settle the platform's cut â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Everyone who travelled pays: those still aboard plus anyone
            // dropped mid-route. Settlement re-splits that total by distance
            // and bills the driver once — a no-op if this ride was already
            // settled through RideService.completeSharedRide.
            List<RidePassenger> travelled = new ArrayList<>(confirmed);
            travelled.addAll(ridePassengerRepository
                    .findByRideAndStatus(ride, RidePassengerStatus.DROPPED));

            rideSettlementService.settlePooledRide(ride, travelled);

            // Tell riders what they were actually charged, not the pre-split
            // estimate computed above.
            for (RideCompletedEvent event : events) {
                travelled.stream()
                        .filter(rp -> rp.getPassenger() != null
                                && rp.getPassenger().getPhoneNumber() != null
                                && rp.getPassenger().getPhoneNumber().equals(event.passengerPhone))
                        .findFirst()
                        .ifPresent(rp -> {
                            if (rp.getFareShare() != null) event.fareAmount = rp.getFareShare();
                            if (rp.getConvenienceFee() != null) {
                                event.convenienceFee = rp.getConvenienceFee().doubleValue();
                            }
                        });
            }

            totalEarnings = travelled.stream()
                    .mapToDouble(rp -> rp.getFareShare() != null ? rp.getFareShare() : 0.0)
                    .sum();

            notificationService.broadcastRideCompleted(ride.getId(), events);
        }

        route.setTotalEarnings(totalEarnings);
        try {
            routeRepository.save(route);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to complete route due to concurrent modification", e);
        }

        // A completed route is no longer bookable — drop it from discovery now.
        evictNearbyRoutesAfterCommit();

        return toResponse(route, ride != null ? ride.getId() : null);
    }

    /** Add a stop/waypoint to a route. */
    @Transactional
    public RouteResponseDTO addStop(Long routeId, RouteAddStopRequest request, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        if (!route.getDriver().getId().equals(driver.getId()))
            throw new SecurityException("Unauthorized: not your route");

        RouteStop stop = RouteStop.builder()
                .route(route)
                .lat(request.lat)
                .lng(request.lng)
                .type(request.type)
                .sequenceOrder(request.sequenceOrder)
                .build();
        routeStopRepository.save(stop);

        Long rideId = rideRepository.findByRoute(route).map(Ride::getId).orElse(null);
        return toResponse(route, rideId);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * This bean as seen through its Spring proxy.
     *
     * <p>Needed so an internal call to {@link #getNearbyRoutesCached} actually
     * passes through the caching interceptor. Calling it directly on
     * {@code this} would skip the proxy and quietly disable the cache — the
     * classic reason a {@code @Cacheable} method "does nothing". Resolved
     * lazily from the context to avoid a circular self-injection at startup.
     */
    private RouteService self() {
        return applicationContext.getBean(RouteService.class);
    }

    /**
     * Drop every cached nearby-routes answer.
     *
     * <p>Called whenever the set of bookable routes changes: a route is
     * created, cancelled or completed. Seat-count changes are deliberately NOT
     * evicted here — they happen on the accept/leave paths in
     * {@code RideService}, and the 8-second TTL covers them. Evicting the whole
     * region on every seat change would leave the cache almost always empty.
     *
     * <p>{@code allEntries} because the cache is keyed by rider position, and a
     * new route affects an unknown set of those keys.
     */
    @CacheEvict(value = CacheConfig.NEARBY_ROUTES_CACHE, allEntries = true)
    public void evictNearbyRoutesCache() {
        // Annotation-driven.
    }

    /**
     * Evict once the surrounding transaction has committed.
     *
     * <p>Evicting mid-transaction is a race: a concurrent reader can miss,
     * re-run the query against not-yet-committed state, and re-populate the
     * cache with the very data the eviction was meant to clear — leaving it
     * stale until the TTL. Worse, if the transaction then rolls back, the cache
     * was cleared for a change that never happened.
     *
     * <p>Mirrors {@code RideService.runAfterCommit}, which exists for the same
     * reason on the WebSocket side.
     */
    private void evictNearbyRoutesAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self().evictNearbyRoutesCache();
                }
            });
        } else {
            self().evictNearbyRoutesCache();
        }
    }

    private RouteResponseDTO toResponse(Route route, Long rideId) {
        double earnings = route.getTotalEarnings() != null ? route.getTotalEarnings() : 0.0;
        return RouteResponseDTO.builder()
                .routeId(route.getId())
                .rideId(rideId)
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
                .earnings(earnings)
                .build();
    }
}
