package com.backend.iniato.services;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RideRepository rideRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final FareRepository fareRepository;
    private final NotificationService notificationService;
    private final FareCalculationService fareCalculationService;

    @Autowired
    public RouteService(RouteRepository routeRepository,
                        RouteStopRepository routeStopRepository,
                        RideRepository rideRepository,
                        RidePassengerRepository ridePassengerRepository,
                        FareRepository fareRepository,
                        NotificationService notificationService,
                        FareCalculationService fareCalculationService) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.rideRepository = rideRepository;
        this.ridePassengerRepository = ridePassengerRepository;
        this.fareRepository = fareRepository;
        this.notificationService = notificationService;
        this.fareCalculationService = fareCalculationService;
    }

    /** Driver declares a new route. Creates Route + linked Ride, broadcasts to nearby riders. */
    @Transactional
    public RouteResponseDTO createRoute(RouteCreateRequest request, User driver) {
        Route route = Route.builder()
                .driver(driver)
                .originLat(request.originLat)
                .originLng(request.originLng)
                .destinationLat(request.destinationLat)
                .destinationLng(request.destinationLng)
                .originAddress(request.originAddress)
                .destinationAddress(request.destinationAddress)
                .totalSeats(request.totalSeats)
                .availableSeats(request.totalSeats)
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
        notificationService.broadcastNewRoute(dto);
        return dto;
    }

    /** Find active routes within ~800 m of the rider's location. */
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getNearbyRoutes(double lat, double lng) {
        double delta = 0.007;
        return routeRepository.findActiveRoutesNear(lat, lng, delta).stream()
                .map(r -> toResponse(r, rideRepository.findByRoute(r).map(Ride::getId).orElse(null)))
                .collect(Collectors.toList());
    }

    /** Driver updates their current location — broadcasts to passengers on the active ride. */
    @Transactional(readOnly = true)
    public void updateLocation(Long routeId, RouteUpdateLocationRequest request, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        if (!route.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Unauthorized: not your route");
        rideRepository.findByRoute(route).ifPresent(ride -> {
            DriverLocationUpdateEvent event = new DriverLocationUpdateEvent();
            event.driverId = String.valueOf(driver.getId());
            event.lat = request.lat;
            event.lng = request.lng;
            notificationService.broadcastLocationUpdate(ride.getId(), event);
        });
    }

    /** Returns all routes (active + completed) for the current driver. */
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getMyRoutes(User driver) {
        return routeRepository.findByDriver(driver).stream()
                .map(r -> toResponse(r, rideRepository.findByRoute(r).map(Ride::getId).orElse(null)))
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
            throw new RuntimeException("Unauthorized: not your route");

        LocalDateTime now = LocalDateTime.now();
        route.setStatus("CANCELLED");
        route.setEndTime(now);
        route.setCompletedAt(now);
        routeRepository.save(route);

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
            throw new RuntimeException("Unauthorized: not your route");

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
                    FareEstimateResponseDTO result = fareCalculationService.calculateSharedFare(req);
                    fare = result.getPerPassengerFare();
                    distKm = result.getDistanceKm();
                }

                rp.setFareShare(fare);
                totalEarnings += fare;

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

            // Add mid-ride DROPPED passengers' earnings too
            totalEarnings += ridePassengerRepository
                    .findByRideAndStatus(ride, RidePassengerStatus.DROPPED).stream()
                    .mapToDouble(rp -> rp.getFareShare() != null ? rp.getFareShare() : 0.0)
                    .sum();

            notificationService.broadcastRideCompleted(ride.getId(), events);
        }

        route.setTotalEarnings(totalEarnings);
        routeRepository.save(route);

        return toResponse(route, ride != null ? ride.getId() : null);
    }

    /** Add a stop/waypoint to a route. */
    @Transactional
    public RouteResponseDTO addStop(Long routeId, RouteAddStopRequest request, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));
        if (!route.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Unauthorized: not your route");

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
                .totalSeats(route.getTotalSeats())
                .availableSeats(route.getAvailableSeats())
                .completedAt(route.getCompletedAt())
                .earnings(earnings)
                .build();
    }
}
