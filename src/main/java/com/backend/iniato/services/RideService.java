package com.backend.iniato.services;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final RideRequestRepository rideRequestRepository;
    private final RouteRepository routeRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final NotificationService notificationService;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    public RideService(RideRepository rideRepository,
                       UserRepository userRepository,
                       RideRequestRepository rideRequestRepository,
                       RouteRepository routeRepository,
                       RidePassengerRepository ridePassengerRepository,
                       NotificationService notificationService) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.routeRepository = routeRepository;
        this.ridePassengerRepository = ridePassengerRepository;
        this.notificationService = notificationService;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByPhoneNumber(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Rider requests to join a specific active ride.
     * Creates a RideRequest + a PENDING RidePassenger, then notifies the driver.
     */
    @Transactional
    public RideResponseDTO requestSharedRide(RideRequestDTO requestDTO) {
        User passenger = getCurrentUser();

        Ride ride = rideRepository.findById(requestDTO.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() != RideStatus.POOL_FORMING) {
            throw new RuntimeException("Ride is not accepting passengers");
        }

        if (ride.getRoute() != null && ride.getRoute().getAvailableSeats() <= 0) {
            throw new RuntimeException("No seats available");
        }

        if (ridePassengerRepository.existsByRideAndPassenger(ride, passenger)) {
            throw new RuntimeException("You have already requested or joined this ride");
        }

        Point pickup = geometryFactory.createPoint(
                new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));
        Point destination = geometryFactory.createPoint(
                new Coordinate(requestDTO.getDestLng(), requestDTO.getDestLat()));

        RideRequest rideRequest = RideRequest.builder()
                .passenger(passenger)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .pickupTime(requestDTO.getPickupTime() != null
                        ? LocalDateTime.parse(requestDTO.getPickupTime())
                        : LocalDateTime.now())
                .status("REQUESTED")
                .matchedRide(ride)
                .build();
        rideRequestRepository.save(rideRequest);

        // Pending row — promoted to CONFIRMED when driver accepts
        RidePassenger ridePassenger = RidePassenger.builder()
                .ride(ride)
                .passenger(passenger)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .status(RidePassengerStatus.PENDING)
                .rideRequest(rideRequest)
                .build();
        ridePassengerRepository.save(ridePassenger);

        if (ride.getDriver() != null) {
            NewRiderRequestEvent event = new NewRiderRequestEvent();
            event.riderId = String.valueOf(passenger.getId());
            event.pickupLat = requestDTO.getPickupLat();
            event.pickupLng = requestDTO.getPickupLng();
            event.dropLat = requestDTO.getDestLat();
            event.dropLng = requestDTO.getDestLng();
            notificationService.notifyDriverNewRequest(ride.getDriver().getId(), event);
        }

        return toResponse(ride);
    }

    /**
     * Returns all rides the current passenger is CONFIRMED on.
     */
    @Transactional(readOnly = true)
    public List<RideResponseDTO> getPassengerSharedRides() {
        User passenger = getCurrentUser();
        return ridePassengerRepository
                .findByPassengerAndStatus(passenger, RidePassengerStatus.CONFIRMED)
                .stream()
                .map(rp -> toResponse(rp.getRide()))
                .collect(Collectors.toList());
    }

    /**
     * Passenger leaves a pooled ride before it starts.
     * Sets their RidePassenger status to LEFT and restores the seat.
     */
    @Transactional
    public RideResponseDTO leaveSharedRide(Long rideId) {
        User passenger = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() != RideStatus.POOL_FORMING) {
            throw new RuntimeException("Cannot leave ride after it has started");
        }

        RidePassenger ridePassenger = ridePassengerRepository
                .findByRideAndPassenger(ride, passenger)
                .orElseThrow(() -> new RuntimeException("You are not part of this ride"));

        if (ridePassenger.getStatus() != RidePassengerStatus.CONFIRMED) {
            throw new RuntimeException("You are not an active passenger on this ride");
        }

        ridePassenger.setStatus(RidePassengerStatus.LEFT);
        ridePassengerRepository.save(ridePassenger);

        if (ride.getRoute() != null) {
            ride.getRoute().setAvailableSeats(ride.getRoute().getAvailableSeats() + 1);
            routeRepository.save(ride.getRoute());
        }

        boolean noConfirmedLeft = ridePassengerRepository
                .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED)
                .isEmpty();
        if (noConfirmedLeft) {
            ride.setStatus(RideStatus.CANCELLED);
            rideRepository.save(ride);
        }

        return toResponse(ride);
    }

    /**
     * Driver sees rides in POOL_FORMING status.
     */
    @Transactional(readOnly = true)
    public List<RideResponseDTO> getAvailableSharedRides() {
        return rideRepository.findByStatus(RideStatus.POOL_FORMING)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns pending RideRequests for a specific ride (driver view).
     */
    @Transactional(readOnly = true)
    public List<RideRequest> getPendingRequests(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));
        return rideRequestRepository.findByMatchedRideAndStatus(ride, "REQUESTED");
    }

    /**
     * Driver accepts a passenger's RideRequest — promotes their RidePassenger to CONFIRMED.
     */
    @Transactional
    public RideResponseDTO acceptPassengerRequest(Long rideId, Long requestId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!driver.equals(ride.getDriver())) {
            throw new RuntimeException("You are not the driver of this ride");
        }

        RideRequest request = rideRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getMatchedRide().getId().equals(rideId)) {
            throw new RuntimeException("Request does not belong to this ride");
        }

        if (!"REQUESTED".equals(request.getStatus())) {
            throw new RuntimeException("Request is no longer pending");
        }

        RidePassenger ridePassenger = ridePassengerRepository
                .findByRideAndPassenger(ride, request.getPassenger())
                .orElseThrow(() -> new RuntimeException("RidePassenger record not found"));

        ridePassenger.setStatus(RidePassengerStatus.CONFIRMED);
        ridePassenger.setJoinedAt(LocalDateTime.now());
        ridePassengerRepository.save(ridePassenger);

        request.setStatus("ACCEPTED");
        rideRequestRepository.save(request);

        if (ride.getRoute() != null && ride.getRoute().getAvailableSeats() > 0) {
            ride.getRoute().setAvailableSeats(ride.getRoute().getAvailableSeats() - 1);
            routeRepository.save(ride.getRoute());
        }

        rideRepository.save(ride);

        PassengerAddedEvent event = new PassengerAddedEvent();
        event.rideId = String.valueOf(rideId);
        event.passengerId = String.valueOf(request.getPassenger().getId());
        notificationService.notifyPassengerAccepted(rideId, event);

        return toResponse(ride);
    }

    /**
     * Driver starts the ride.
     */
    @Transactional
    public RideResponseDTO startSharedRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!driver.equals(ride.getDriver())) {
            throw new RuntimeException("You are not authorized to start this ride");
        }

        if (ridePassengerRepository.findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED).isEmpty()) {
            throw new RuntimeException("No confirmed passengers on board yet");
        }

        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.STARTED);
        rideRepository.save(ride);

        notificationService.broadcastRideStarted(rideId);
        return toResponse(ride);
    }

    /**
     * Driver completes the ride.
     * Marks all CONFIRMED passengers as COMPLETED.
     */
    @Transactional
    public RideResponseDTO completeSharedRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!driver.equals(ride.getDriver())) {
            throw new RuntimeException("Unauthorized driver");
        }

        ride.setEndTime(LocalDateTime.now());
        ride.setStatus(RideStatus.COMPLETED);
        rideRepository.save(ride);

        List<RidePassenger> confirmed = ridePassengerRepository
                .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED);
        confirmed.forEach(rp -> rp.setStatus(RidePassengerStatus.COMPLETED));
        ridePassengerRepository.saveAll(confirmed);

        notificationService.broadcastRideCompleted(rideId);
        return toResponse(ride);
    }

    private RideResponseDTO toResponse(Ride ride) {
        List<String> passengerPhones = ridePassengerRepository
                .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED)
                .stream()
                .map(rp -> rp.getPassenger().getPhoneNumber())
                .collect(Collectors.toList());

        return RideResponseDTO.builder()
                .rideId(ride.getId())
                .driverEmail(ride.getDriver() != null ? ride.getDriver().getEmail() : null)
                .pickupLocation(ride.getPickupLocation())
                .destination(ride.getDestination())
                .requestedTime(ride.getRequestedTime())
                .status(ride.getStatus())
                .passengers(passengerPhones)
                .build();
    }
}
