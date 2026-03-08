package com.backend.iniato.services;

import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideResponseDTO;
import com.backend.iniato.dto.RiderBookRouteRequest;
import com.backend.iniato.entity.Booking;
import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.RideRepository;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RideService {

    @Autowired
    private final RideRepository rideRepository;
    @Autowired
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String phone = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Passenger requests to join a shared ride.
     * If an existing ride matches the route, add the passenger to it.
     * Else, create a new pooled ride.
     */
    public RideResponseDTO requestSharedRide(RideRequestDTO requestDTO) {
        User passenger = getCurrentUser();

        // 1️⃣ Find an existing active ride going in the same direction
        List<Ride> potentialRides = rideRepository.findActiveRidesByRoute(
                requestDTO.getPickupLocaton(), requestDTO.getDestination());

        Ride ride;
        if (!potentialRides.isEmpty()) {
            // Join first available shared ride
            ride = potentialRides.get(0);
            ride.getPassengers().add(passenger);
        } else {
            // Create new ride
            ride = Ride.builder()
                    .pickupLocation(requestDTO.getPickupLocaton())
                    .destination(requestDTO.getDestination())
                    .requestedTime(LocalDateTime.now())
                    .status(RideStatus.POOL_FORMING)
                    .passengers(List.of(passenger))
                    .build();
        }

        rideRepository.save(ride);
        return toResponse(ride);
    }

    /**
     * All rides the current passenger is part of.
     */
    public List<RideResponseDTO> getPassengerSharedRides() {
        User passenger = getCurrentUser();
        return rideRepository.findByPassengers(passenger)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Passenger leaves a pooled ride before it starts.
     */
    public RideResponseDTO leaveSharedRide(Long rideId) {
        User passenger = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!ride.getPassengers().contains(passenger)) {
            throw new RuntimeException("You are not part of this ride");
        }

        if (ride.getStatus() != RideStatus.POOL_FORMING) {
            throw new RuntimeException("Cannot leave ride after it has started");
        }

        ride.getPassengers().remove(passenger);

        // If no passengers left, cancel the ride
        if (ride.getPassengers().isEmpty()) {
            ride.setStatus(RideStatus.CANCELLED);
        }

        rideRepository.save(ride);
        return toResponse(ride);
    }

    /**
     * Drivers can view available pooled rides that need a driver.
     */
    public List<RideResponseDTO> getAvailableSharedRides() {
        return rideRepository.findByStatus(RideStatus.POOL_FORMING)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Driver accepts to host a shared ride.
     */
    public RideResponseDTO acceptSharedRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getDriver() != null) {
            throw new RuntimeException("Ride already has a driver assigned");
        }

        ride.setDriver(driver);
        ride.setAcceptedTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACCEPTED);
        rideRepository.save(ride);

        return toResponse(ride);
    }

    /**
     * Driver starts the shared ride (after enough passengers join).
     */
    public RideResponseDTO startSharedRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!driver.equals(ride.getDriver())) {
            throw new RuntimeException("You are not authorized to start this ride");
        }

        if (ride.getPassengers().size() < 2) {
            throw new RuntimeException("Minimum 2 passengers required to start pooling ride");
        }

        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.STARTED);
        rideRepository.save(ride);

        return toResponse(ride);
    }

    /**
     * Ride completed — triggers fare split logic downstream.
     */
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

        return toResponse(ride);
    }

    public Booking bookRoute(RiderBookRouteRequest request) {
        // Implementation for booking a route
        return null;
    }

    public boolean cancelBooking(String bookingId) {
        // Implementation for canceling a booking
        return false;
    }

    private RideResponseDTO toResponse(Ride ride) {
        return RideResponseDTO.builder()
                .rideId(ride.getId())
                .driverEmail(ride.getDriver() != null ? ride.getDriver().getEmail() : null)
                .pickupLocation(ride.getPickupLocation())
                .destination(ride.getDestination())
                .requestedTime(ride.getRequestedTime())
                .status(ride.getStatus())
                .passengers(ride.getPassengers()
                        .stream().map(User::getPhoneNumber).collect(Collectors.toList()))
                .build();
    }
}
