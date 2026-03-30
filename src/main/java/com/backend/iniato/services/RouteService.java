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

    private static final double BASE_FARE = 20.0;
    private static final double PRICE_PER_KM = 10.0;
    private static final double POOL_DISCOUNT = 0.15;

    @Autowired
    public RouteService(RouteRepository routeRepository,
                        RouteStopRepository routeStopRepository,
                        RideRepository rideRepository,
                        RidePassengerRepository ridePassengerRepository,
                        FareRepository fareRepository,
                        NotificationService notificationService) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.rideRepository = rideRepository;
        this.ridePassengerRepository = ridePassengerRepository;
        this.fareRepository = fareRepository;
        this.notificationService = notificationService;
    }

    /**
     * Driver declares a new route. Creates Route + linked Ride session, broadcasts to nearby riders.
     */
    @Transactional
    public RouteResponseDTO createRoute(RouteCreateRequest request, User driver) {
        Route route = Route.builder()
                .driver(driver)
                .originLat(request.originLat)
                .originLng(request.originLng)
                .destinationLat(request.destinationLat)
                .destinationLng(request.destinationLng)
                .totalSeats(request.totalSeats)
                .availableSeats(request.totalSeats)
                .status("ACTIVE")
                .startTime(LocalDateTime.now())
                .build();
        routeRepository.save(route);

        Ride ride = Ride.builder()
                .driver(driver)
                .route(route)
                .requestedTime(LocalDateTime.now())
                .status(RideStatus.POOL_FORMING)
                .build();
        rideRepository.save(ride);

        RouteResponseDTO dto = toResponse(route, ride.getId());
        notificationService.broadcastNewRoute(dto);
        return dto;
    }

    /**
     * Find active routes within ~800m corridor of the rider's location.
     */
    @Transactional(readOnly = true)
    public List<RouteResponseDTO> getNearbyRoutes(double lat, double lng) {
        double delta = 0.007; // ~800m bounding box
        return routeRepository.findActiveRoutesNear(lat, lng, delta).stream()
                .map(r -> {
                    Long rideId = rideRepository.findByRoute(r)
                            .map(Ride::getId)
                            .orElse(null);
                    return toResponse(r, rideId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Driver updates their current location — broadcasts to passengers on the active ride.
     */
    @Transactional(readOnly = true)
    public void updateLocation(Long routeId, RouteUpdateLocationRequest request, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));

        if (!route.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("Unauthorized: not your route");
        }

        rideRepository.findByRoute(route).ifPresent(ride -> {
            DriverLocationUpdateEvent event = new DriverLocationUpdateEvent();
            event.driverId = String.valueOf(driver.getId());
            event.lat = request.lat;
            event.lng = request.lng;
            notificationService.broadcastLocationUpdate(ride.getId(), event);
        });
    }

    /**
     * Driver adds a pickup/drop stop to their route.
     */
    @Transactional
    public RouteResponseDTO addStop(Long routeId, RouteAddStopRequest request, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));

        if (!route.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("Unauthorized: not your route");
        }

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

    /**
     * Driver marks route as completed.
     * Cascades: also completes the associated ride, marks passengers as COMPLETED,
     * calculates fare, and populates fareShare.
     */
    @Transactional
    public RouteResponseDTO completeRoute(Long routeId, User driver) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found"));

        if (!route.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("Unauthorized: not your route");
        }

        // 1. Mark route as completed
        route.setStatus("COMPLETED");
        routeRepository.save(route);

        // 2. Cascade to the linked ride
        rideRepository.findByRoute(route).ifPresent(ride -> {
            ride.setEndTime(LocalDateTime.now());
            ride.setStatus(RideStatus.COMPLETED);
            rideRepository.save(ride);

            // 3. Mark all confirmed passengers as completed
            List<RidePassenger> confirmed = ridePassengerRepository
                    .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED);
            confirmed.forEach(rp -> rp.setStatus(RidePassengerStatus.COMPLETED));
            ridePassengerRepository.saveAll(confirmed);

            // 4. Calculate fare and populate Fare entity + fareShare
            if (!confirmed.isEmpty()) {
                double distanceKm = calculateDistance(
                        route.getOriginLat(), route.getOriginLng(),
                        route.getDestinationLat(), route.getDestinationLng());

                double totalFare = BASE_FARE + (distanceKm * PRICE_PER_KM);
                double discountedFare = totalFare * (1 - POOL_DISCOUNT);
                double perPassengerFare = discountedFare / confirmed.size();

                // Save Fare entity
                Fare fare = Fare.builder()
                        .ride(ride)
                        .distanceKm(distanceKm)
                        .totalFare(discountedFare)
                        .perPassengerFare(perPassengerFare)
                        .build();
                fareRepository.save(fare);

                // Populate fareShare on each passenger
                confirmed.forEach(rp -> rp.setFareShare(perPassengerFare));
                ridePassengerRepository.saveAll(confirmed);
            }

            notificationService.broadcastRideCompleted(ride.getId());
        });

        Long rideId = rideRepository.findByRoute(route).map(Ride::getId).orElse(null);
        return toResponse(route, rideId);
    }

    /**
     * Haversine distance calculation.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private RouteResponseDTO toResponse(Route route, Long rideId) {
        return RouteResponseDTO.builder()
                .routeId(route.getId())
                .rideId(rideId)
                .driverPhone(route.getDriver() != null ? route.getDriver().getPhoneNumber() : null)
                .status(route.getStatus())
                .originLat(route.getOriginLat())
                .originLng(route.getOriginLng())
                .destinationLat(route.getDestinationLat())
                .destinationLng(route.getDestinationLng())
                .totalSeats(route.getTotalSeats())
                .availableSeats(route.getAvailableSeats())
                .build();
    }
}
