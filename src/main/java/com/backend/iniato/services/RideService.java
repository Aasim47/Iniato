package com.backend.iniato.services;


import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideResponseDTO;
import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.RideRepository;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // 1. Passenger requests ride
    public RideResponseDTO requestRide(RideRequestDTO requestDTO) {
        User passenger = getCurrentUser();

        Ride ride = Ride.builder()
                .pickupLocation(requestDTO.getPickupLocation())
                .destination(requestDTO.getDestination())
                .requestedTime(LocalDateTime.now())
                .status(RideStatus.REQUESTED)
                .passenger(passenger)
                .build();

        rideRepository.save(ride);
        return toResponse(ride);
    }

    // 2. Passenger views their rides
    public List<RideResponseDTO> getPassengerRides() {
        User passenger = getCurrentUser();
        return rideRepository.findByPassenger(passenger)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 3. Passenger cancels a ride
    public RideResponseDTO cancelRide(Long rideId) {
        User passenger = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!ride.getPassenger().equals(passenger)) {
            throw new RuntimeException("Not authorized to cancel this ride");
        }

        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new RuntimeException("Cannot cancel completed or cancelled ride");
        }

        ride.setStatus(RideStatus.CANCELLED);
        rideRepository.save(ride);
        return toResponse(ride);
    }

    // 4. Driver views available rides
    public List<RideResponseDTO> getAvailableRides() {
        return rideRepository.findByStatus(RideStatus.REQUESTED)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 5. Driver accepts a ride
    public RideResponseDTO acceptRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new RuntimeException("Ride already accepted or completed");
        }

        ride.setDriver(driver);
        ride.setAcceptedTime(LocalDateTime.now());
        ride.setStatus(RideStatus.ACCEPTED);
        rideRepository.save(ride);
        return toResponse(ride);
    }

    // 6. Driver starts trip
    public RideResponseDTO startRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!ride.getDriver().equals(driver)) {
            throw new RuntimeException("Not authorized to start this ride");
        }

        ride.setStartTime(LocalDateTime.now());
        ride.setStatus(RideStatus.STARTED);
        rideRepository.save(ride);
        return toResponse(ride);
    }

    // 7. Driver completes trip
    public RideResponseDTO completeRide(Long rideId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!ride.getDriver().equals(driver)) {
            throw new RuntimeException("Not authorized to complete this ride");
        }

        ride.setEndTime(LocalDateTime.now());
        ride.setStatus(RideStatus.COMPLETED);
        rideRepository.save(ride);
        return toResponse(ride);
    }

    private RideResponseDTO toResponse(Ride ride) {
        return RideResponseDTO.builder()
                .rideId(ride.getId())
                .passengerEmail(ride.getPassenger().getEmail())
                .driverEmail(ride.getDriver() != null ? ride.getDriver().getEmail() : null)
                .pickupLocation(ride.getPickupLocation())
                .destination(ride.getDestination())
                .requestedTime(ride.getRequestedTime())
                .status(ride.getStatus())
                .build();
    }
}
