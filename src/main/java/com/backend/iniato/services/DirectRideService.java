package com.backend.iniato.services;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.*;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DirectRideService {

    private static final int SRID = 4326;
    private final DirectRideRepository directRideRepository;
    private final UserRepository userRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final RideMatchingService rideMatchingService;
    private final FareCalculationService fareCalculationService;
    private final NotificationService notificationService;
    private final PassengerProfileRepository passengerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final RideSettlementService rideSettlementService;
    private final DriverDuesService driverDuesService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    @Autowired
    public DirectRideService(DirectRideRepository directRideRepository,
                            UserRepository userRepository,
                            DriverLocationRepository driverLocationRepository,
                            RideMatchingService rideMatchingService,
                            FareCalculationService fareCalculationService,
                            NotificationService notificationService,
                            PassengerProfileRepository passengerProfileRepository,
                            DriverProfileRepository driverProfileRepository,
                            RideSettlementService rideSettlementService,
                            DriverDuesService driverDuesService) {
        this.rideSettlementService = rideSettlementService;
        this.driverDuesService = driverDuesService;
        this.directRideRepository = directRideRepository;
        this.userRepository = userRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.rideMatchingService = rideMatchingService;
        this.fareCalculationService = fareCalculationService;
        this.notificationService = notificationService;
        this.passengerProfileRepository = passengerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    @Transactional
    public DirectRideResponseDTO createDirectRide(DirectRideRequestDTO request, User passenger) {
        Point pickup = geometryFactory.createPoint(
                new Coordinate(request.getPickupLng(), request.getPickupLat()));
        pickup.setSRID(SRID);
        Point destination = geometryFactory.createPoint(
                new Coordinate(request.getDestLng(), request.getDestLat()));
        destination.setSRID(SRID);

        DirectRide directRide = DirectRide.builder()
                .passenger(passenger)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .pickupLocationName(request.getPickupLocationName())
                .destinationName(request.getDestinationName())
                .requestedTime(LocalDateTime.now())
                .status(RideStatus.REQUESTED)
                .estimatedFare(calculateEstimatedFare(request))
                .vehicleType(request.getVehicleType())
                .passengerCount(request.getPassengerCount())
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .destinationLat(request.getDestLat())
                .destinationLng(request.getDestLng())
                .build();

        DirectRide savedRide = directRideRepository.save(directRide);

        DirectRideResponseDTO response = toResponse(savedRide);
        notificationService.broadcastDirectRideRequest(response);

        return response;
    }

    @Transactional
    public DirectRideResponseDTO acceptDirectRide(Long rideId, User driver) {
        DirectRide directRide = directRideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Direct ride not found"));

        // Accepting a ride is taking on new work — blocked while dues are unpaid.
        driverDuesService.assertCanDrive(driver);

        if (directRide.getStatus() != RideStatus.REQUESTED) {
            throw new IllegalStateException("Ride is not available for acceptance");
        }

        directRide.setDriver(driver);
        directRide.setAcceptedTime(LocalDateTime.now());
        directRide.setStatus(RideStatus.ACCEPTED);

        DirectRide savedRide = directRideRepository.save(directRide);

        DirectRideResponseDTO response = toResponse(savedRide);
        notificationService.notifyDriverDirectRideAccepted(response);

        return response;
    }

    @Transactional
    public DirectRideResponseDTO declineDirectRide(Long rideId, User driver) {
        DirectRide directRide = directRideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Direct ride not found"));

        if (directRide.getStatus() != RideStatus.REQUESTED) {
            throw new IllegalStateException("Ride is not available for decline");
        }

        directRide.setStatus(RideStatus.CANCELLED);
        DirectRide savedRide = directRideRepository.save(directRide);

        notificationService.notifyDriverDirectRideDeclined(toResponse(savedRide));

        return toResponse(savedRide);
    }

    @Transactional
    public DirectRideResponseDTO cancelDirectRideByPassenger(Long rideId, User passenger) {
        DirectRide directRide = directRideRepository.findByIdAndPassenger(rideId, passenger)
                .orElseThrow(() -> new RuntimeException("Direct ride not found or unauthorized"));

        if (directRide.getStatus() == RideStatus.COMPLETED || directRide.getStatus() == RideStatus.CANCELLED) {
            throw new IllegalStateException("Ride cannot be cancelled in its current state");
        }

        directRide.setStatus(RideStatus.CANCELLED);
        DirectRide savedRide = directRideRepository.save(directRide);

        notificationService.broadcastDirectRideCancelled(toResponse(savedRide));
        return toResponse(savedRide);
    }

    @Transactional
    public DirectRideResponseDTO cancelDirectRideByDriver(Long rideId, User driver) {
        DirectRide directRide = directRideRepository.findByIdAndDriver(rideId, driver)
                .orElseThrow(() -> new RuntimeException("Direct ride not found or unauthorized"));

        if (directRide.getStatus() == RideStatus.COMPLETED || directRide.getStatus() == RideStatus.CANCELLED) {
            throw new IllegalStateException("Ride cannot be cancelled in its current state");
        }

        directRide.setStatus(RideStatus.CANCELLED);
        DirectRide savedRide = directRideRepository.save(directRide);

        notificationService.broadcastDirectRideCancelled(toResponse(savedRide));
        return toResponse(savedRide);
    }

    @Transactional
    public DirectRideResponseDTO startDirectRide(Long rideId, User driver) {
        DirectRide directRide = directRideRepository.findByIdAndDriver(rideId, driver)
                .orElseThrow(() -> new RuntimeException("Direct ride not found or unauthorized"));

        if (directRide.getStatus() != RideStatus.ACCEPTED) {
            throw new IllegalStateException("Ride is not in ACCEPTED status");
        }

        directRide.setStartTime(LocalDateTime.now());
        directRide.setStatus(RideStatus.STARTED);

        DirectRide savedRide = directRideRepository.save(directRide);

        DirectRideResponseDTO response = toResponse(savedRide);
        notificationService.broadcastDirectRideStarted(response);

        return response;
    }

    @Transactional
    public DirectRideResponseDTO completeDirectRide(Long rideId, User driver) {
        DirectRide directRide = directRideRepository.findByIdAndDriver(rideId, driver)
                .orElseThrow(() -> new RuntimeException("Direct ride not found or unauthorized"));

        if (directRide.getStatus() != RideStatus.STARTED) {
            throw new IllegalStateException("Ride is not in STARTED status");
        }

        directRide.setEndTime(LocalDateTime.now());
        directRide.setStatus(RideStatus.COMPLETED);

        DirectRide savedRide = directRideRepository.save(directRide);

        // Bill the platform's cut. The STARTED guard above already prevents a
        // repeat, and settlement is idempotent regardless.
        rideSettlementService.settleDirectRide(savedRide);

        DirectRideResponseDTO response = toResponse(savedRide);
        notificationService.broadcastDirectRideCompleted(response);

        return response;
    }

    public List<DirectRideResponseDTO> getPassengerRides(User passenger) {
        return directRideRepository.findByPassenger(passenger).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Fetch a single direct ride owned by the passenger (O(1) status poll). */
    public DirectRideResponseDTO getPassengerRideById(Long rideId, User passenger) {
        DirectRide ride = directRideRepository.findByIdAndPassenger(rideId, passenger)
                .orElseThrow(() -> new RuntimeException("Direct ride not found"));
        return toResponse(ride);
    }

    public List<DirectRideResponseDTO> getDriverRides(User driver) {
        return directRideRepository.findByDriver(driver).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<DirectRideResponseDTO> getNearbyAvailableRides(double lat, double lng, double radiusMeters) {
        List<DirectRide> availableRides = directRideRepository.findAvailableRidesWithinRadius(RideStatus.REQUESTED, lat, lng, radiusMeters);

        return availableRides.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Double estimateDirectRideFare(DirectRideRequestDTO request) {
        return calculateEstimatedFare(request);
    }

    /** Fare plus the convenience fee carved out of it, for the rider's quote. */
    public FareEstimateResponseDTO estimateDirectRideFareDetailed(DirectRideRequestDTO request) {
        return fareCalculationService.calculateSharedFare(toFareRequest(request));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000;
    }

    private double calculateEstimatedFare(DirectRideRequestDTO request) {
        return fareCalculationService
                .calculateSharedFare(toFareRequest(request))
                .getPerPassengerFare();
    }

    /** The fee actually charged on a settled ride, or null if not yet settled. */
    private Double settledConvenienceFee(DirectRide directRide) {
        if (directRide.getId() == null || directRide.getStatus() != RideStatus.COMPLETED) {
            return null;
        }
        return rideSettlementService
                .findSettlement(com.backend.iniato.enums.RideKind.DIRECT, directRide.getId())
                .map(s -> s.getConvenienceFee() == null ? null : s.getConvenienceFee().doubleValue())
                .orElse(null);
    }

    private FareEstimateRequestDTO toFareRequest(DirectRideRequestDTO request) {
        FareEstimateRequestDTO fareRequest = new FareEstimateRequestDTO();
        fareRequest.setPickupLat(request.getPickupLat());
        fareRequest.setPickupLng(request.getPickupLng());
        fareRequest.setDestLat(request.getDestLat());
        fareRequest.setDestLng(request.getDestLng());
        fareRequest.setPassengers(request.getPassengerCount());
        fareRequest.setVehicleType(request.getVehicleType());
        return fareRequest;
    }

    private DirectRideResponseDTO toResponse(DirectRide directRide) {
        String passengerName = null;
        if (directRide.getPassenger() != null) {
            Optional<PassengerProfile> passengerProfile = passengerProfileRepository.findByUser(directRide.getPassenger());
            if (passengerProfile.isPresent()) {
                passengerName = passengerProfile.get().getFullName();
            }
        }

        String driverName = null;
        Double driverRating = null;
        String driverVehicleType = directRide.getVehicleType();
        String driverVehicleNumber = directRide.getVehicleNumber();

        if (directRide.getDriver() != null) {
            Optional<DriverProfile> driverProfile = driverProfileRepository.findByUser(directRide.getDriver());
            if (driverProfile.isPresent()) {
                driverName = driverProfile.get().getFullName();
                driverRating = driverProfile.get().getAverageRating();
                if (driverVehicleType == null && driverProfile.get().getVehicleType() != null) {
                    driverVehicleType = driverProfile.get().getVehicleType().name();
                }
                if (driverVehicleNumber == null) driverVehicleNumber = driverProfile.get().getVehicleRegistration();
            }
        }

        return DirectRideResponseDTO.builder()
                .id(directRide.getId())
                .driverId(directRide.getDriver() != null ? directRide.getDriver().getId() : null)
                .passengerId(directRide.getPassenger() != null ? directRide.getPassenger().getId() : null)
                .pickupLat(directRide.getPickupLat())
                .pickupLng(directRide.getPickupLng())
                .destinationLat(directRide.getDestinationLat())
                .destinationLng(directRide.getDestinationLng())
                .pickupLocationName(directRide.getPickupLocationName())
                .destinationName(directRide.getDestinationName())
                .requestedTime(directRide.getRequestedTime())
                .acceptedTime(directRide.getAcceptedTime())
                .startTime(directRide.getStartTime())
                .endTime(directRide.getEndTime())
                .status(directRide.getStatus())
                .estimatedFare(directRide.getEstimatedFare())
                .vehicleType(driverVehicleType)
                .vehicleNumber(driverVehicleNumber)
                .passengerCount(directRide.getPassengerCount())
                .passengerName(passengerName)
                .driverName(driverName)
                .driverRating(driverRating)
                // Only known once settled, so the rider's receipt can show the
                // fee while a pre-ride quote simply omits it.
                .convenienceFee(settledConvenienceFee(directRide))
                .build();
    }
}