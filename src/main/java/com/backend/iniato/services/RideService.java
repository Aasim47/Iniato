package com.backend.iniato.services;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final RideRequestRepository rideRequestRepository;
    private final RouteRepository routeRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final PassengerProfileRepository passengerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final NotificationService notificationService;
    private final FareCalculationService fareCalculationService;
    private static final int SRID = 4326;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    /** Max angular difference (degrees) allowed between route and passenger direction vectors. */
    private static final double MAX_DIRECTION_ANGLE_DEG = 60.0;
    /** Max perpendicular distance (metres) of passenger pickup from route corridor. */
    private static final double MAX_CORRIDOR_DEVIATION_METERS = 1500.0;
    private static final double METERS_PER_DEGREE = 111_000.0;

    @Autowired
    public RideService(RideRepository rideRepository,
                       UserRepository userRepository,
                       RideRequestRepository rideRequestRepository,
                       RouteRepository routeRepository,
                       RidePassengerRepository ridePassengerRepository,
                       PassengerProfileRepository passengerProfileRepository,
                       DriverProfileRepository driverProfileRepository,
                       NotificationService notificationService,
                       FareCalculationService fareCalculationService) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.routeRepository = routeRepository;
        this.ridePassengerRepository = ridePassengerRepository;
        this.passengerProfileRepository = passengerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.notificationService = notificationService;
        this.fareCalculationService = fareCalculationService;
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
     *
     * <p>Before accepting the request the service validates that the passenger's
     * pickup→destination vector is directionally compatible with the ride's route
     * (same general direction, pickup within the route corridor). Requests that
     * would require the driver to backtrack or deviate significantly are rejected.
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

        // Prevent passenger from having multiple active rides simultaneously
        boolean alreadyInActiveRide = ridePassengerRepository
                .findByPassenger(passenger)
                .stream()
                .anyMatch(rp ->
                    (rp.getStatus() == RidePassengerStatus.CONFIRMED ||
                     rp.getStatus() == RidePassengerStatus.PENDING) &&
                    (rp.getRide().getStatus() == RideStatus.POOL_FORMING ||
                     rp.getRide().getStatus() == RideStatus.STARTED));
        if (alreadyInActiveRide) {
            throw new RuntimeException(
                    "You already have an active ride. Please complete or cancel it before joining another.");
        }

        // ── Direction compatibility gate ─────────────────────────────────────
        // Ensure the passenger is travelling in the same general direction as the
        // driver's route. If not, reject immediately so the passenger is not shown
        // (or able to join) a ride heading the opposite way.
        if (ride.getRoute() != null) {
            Route route = ride.getRoute();
            if (route.getOriginLat() != null && route.getDestinationLat() != null) {
                if (!isDirectionCompatible(route,
                        requestDTO.getPickupLat(), requestDTO.getPickupLng(),
                        requestDTO.getDestLat(),   requestDTO.getDestLng())) {
                    throw new RuntimeException(
                            "Your destination is not in the same direction as this ride. " +
                            "Please search for a ride that matches your route.");
                }
                if (!isPickupWithinCorridor(route,
                        requestDTO.getPickupLat(), requestDTO.getPickupLng())) {
                    throw new RuntimeException(
                            "Your pickup location is too far from this ride's route corridor.");
                }
            }
        }
        // ────────────────────────────────────────────────────────────────────

        Point pickup = geometryFactory.createPoint(
                new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));
        pickup.setSRID(SRID);
        Point destination = geometryFactory.createPoint(
                new Coordinate(requestDTO.getDestLng(), requestDTO.getDestLat()));
        destination.setSRID(SRID);

        RideRequest rideRequest = RideRequest.builder()
                .passenger(passenger)
                .pickupLocation(pickup)
                .destinationLocation(destination)
                .pickupLocationName(requestDTO.getPickupLocation())
                .destinationName(requestDTO.getDestination())
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
            event.rideId = String.valueOf(ride.getId());
            event.requestId = String.valueOf(rideRequest.getId());
            event.riderId = String.valueOf(passenger.getId());
            event.pickupLat = requestDTO.getPickupLat();
            event.pickupLng = requestDTO.getPickupLng();
            event.dropLat = requestDTO.getDestLat();
            event.dropLng = requestDTO.getDestLng();
            notificationService.notifyDriverNewRequest(ride.getDriver().getId(), event);
        }

        return toResponse(ride);
    }

    // -----------------------------------------------------------------------
    // Direction helpers (duplicated here to keep RideService self-contained)
    // -----------------------------------------------------------------------

    /**
     * Returns true when the passenger's travel vector is within
     * {@value MAX_DIRECTION_ANGLE_DEG}° of the route's direction vector.
     */
    private boolean isDirectionCompatible(Route route,
                                          double pPickupLat, double pPickupLng,
                                          double pDestLat,   double pDestLng) {
        double midLat = (route.getOriginLat() + route.getDestinationLat()) / 2.0;
        double cosLat = Math.cos(Math.toRadians(midLat));

        double rVecLat = route.getDestinationLat() - route.getOriginLat();
        double rVecLng = (route.getDestinationLng() - route.getOriginLng()) * cosLat;

        double pVecLat = pDestLat - pPickupLat;
        double pVecLng = (pDestLng - pPickupLng) * cosLat;

        double dot  = rVecLat * pVecLat + rVecLng * pVecLng;
        double rMag = Math.sqrt(rVecLat * rVecLat + rVecLng * rVecLng);
        double pMag = Math.sqrt(pVecLat * pVecLat + pVecLng * pVecLng);

        if (rMag < 1e-9 || pMag < 1e-9) return false;

        double cosAngle = Math.max(-1.0, Math.min(1.0, dot / (rMag * pMag)));
        return Math.toDegrees(Math.acos(cosAngle)) <= MAX_DIRECTION_ANGLE_DEG;
    }

    /**
     * Returns true when the passenger's pickup is within
     * {@value MAX_CORRIDOR_DEVIATION_METERS} m of the route's straight-line corridor.
     */
    private boolean isPickupWithinCorridor(Route route, double pLat, double pLng) {
        double aLat = route.getOriginLat(),      aLng = route.getOriginLng();
        double bLat = route.getDestinationLat(), bLng = route.getDestinationLng();
        double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));

        double ax = aLng * cosLat * METERS_PER_DEGREE, ay = aLat * METERS_PER_DEGREE;
        double bx = bLng * cosLat * METERS_PER_DEGREE, by = bLat * METERS_PER_DEGREE;
        double px = pLng * cosLat * METERS_PER_DEGREE, py = pLat * METERS_PER_DEGREE;

        double abx = bx - ax, aby = by - ay;
        double segLenSq = abx * abx + aby * aby;
        if (segLenSq < 1e-9) {
            double dx = px - ax, dy = py - ay;
            return Math.sqrt(dx * dx + dy * dy) <= MAX_CORRIDOR_DEVIATION_METERS;
        }
        double cross = Math.abs(abx * (ay - py) - aby * (ax - px));
        return (cross / Math.sqrt(segLenSq)) <= MAX_CORRIDOR_DEVIATION_METERS;
    }

    // -----------------------------------------------------------------------
    // Remaining service methods
    // -----------------------------------------------------------------------

    /**
     * Returns ALL rides the current passenger has ever been part of (full history).
     * Includes PENDING, CONFIRMED, DROPPED, COMPLETED, LEFT, REJECTED statuses.
     */
    @Transactional(readOnly = true)
    public List<RideResponseDTO> getPassengerSharedRides() {
        User passenger = getCurrentUser();
        return ridePassengerRepository
                .findByPassenger(passenger)
                .stream()
                .map(rp -> toResponse(rp.getRide()))
                // deduplicate by rideId (passenger can have multiple rows for same ride)
                .collect(java.util.stream.Collectors.toMap(
                        RideResponseDTO::getRideId,
                        r -> r,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values()
                .stream()
                .sorted(java.util.Comparator.comparing(
                        r -> r.getRequestedTime() != null ? r.getRequestedTime() : java.time.LocalDateTime.MIN,
                        java.util.Comparator.reverseOrder()))
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

        // Allow both PENDING (not yet accepted) and CONFIRMED passengers to leave
        if (ridePassenger.getStatus() != RidePassengerStatus.CONFIRMED
                && ridePassenger.getStatus() != RidePassengerStatus.PENDING) {
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
            // Notify driver that the ride was auto-cancelled
            if (ride.getDriver() != null) {
                RideCancelledEvent event = new RideCancelledEvent();
                event.rideId = String.valueOf(rideId);
                event.reason = "All passengers left";
                notificationService.notifyDriverRideCancelled(ride.getDriver().getId(), event);
            }
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

        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
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

        // Lock the Route row to prevent concurrent overbooking.
        // Re-check seat availability under the lock before confirming.
        if (ride.getRoute() != null) {
            Route lockedRoute = routeRepository.findByIdForUpdate(ride.getRoute().getId())
                    .orElseThrow(() -> new RuntimeException("Route not found"));
            if (lockedRoute.getAvailableSeats() <= 0) {
                throw new RuntimeException("No seats available — ride is full");
            }
            lockedRoute.setAvailableSeats(lockedRoute.getAvailableSeats() - 1);
            routeRepository.save(lockedRoute);
        }

        RidePassenger ridePassenger = ridePassengerRepository
                .findByRideAndPassenger(ride, request.getPassenger())
                .orElseThrow(() -> new RuntimeException("RidePassenger record not found"));

        ridePassenger.setStatus(RidePassengerStatus.CONFIRMED);
        ridePassenger.setJoinedAt(LocalDateTime.now());
        ridePassengerRepository.save(ridePassenger);

        request.setStatus("ACCEPTED");
        rideRequestRepository.save(request);

        PassengerAddedEvent event = new PassengerAddedEvent();
        event.rideId = String.valueOf(rideId);
        event.passengerId = String.valueOf(request.getPassenger().getId());
        event.passengerPhone = request.getPassenger().getPhoneNumber();
        event.passengerEmail = request.getPassenger().getEmail() != null
                ? request.getPassenger().getEmail() : request.getPassenger().getPhoneNumber();
        notificationService.notifyPassengerAccepted(rideId, event);

        // Broadcast updated seat count so riders waiting on RouteMatchScreen see it in real time.
        if (ride.getRoute() != null) {
            Route updatedRoute = routeRepository.findById(ride.getRoute().getId()).orElse(ride.getRoute());
            RouteResponseDTO routeUpdate = RouteResponseDTO.builder()
                    .routeId(updatedRoute.getId())
                    .rideId(rideId)
                    .driverPhone(updatedRoute.getDriver() != null ? updatedRoute.getDriver().getPhoneNumber() : null)
                    .status(updatedRoute.getStatus())
                    .originLat(updatedRoute.getOriginLat())
                    .originLng(updatedRoute.getOriginLng())
                    .destinationLat(updatedRoute.getDestinationLat())
                    .destinationLng(updatedRoute.getDestinationLng())
                    .originAddress(updatedRoute.getOriginAddress())
                    .destinationAddress(updatedRoute.getDestinationAddress())
                    .totalSeats(updatedRoute.getTotalSeats())
                    .availableSeats(updatedRoute.getAvailableSeats())
                    .earnings(0.0)
                    .build();
            notificationService.broadcastRouteUpdated(routeUpdate);
        }

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

        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
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

        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("Unauthorized driver");
        }

        ride.setEndTime(LocalDateTime.now());
        ride.setStatus(RideStatus.COMPLETED);
        rideRepository.save(ride);

        List<RidePassenger> confirmed = ridePassengerRepository
                .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED);

        List<RideCompletedEvent> events = new ArrayList<>();
        for (RidePassenger rp : confirmed) {
            rp.setStatus(RidePassengerStatus.COMPLETED);
            double fare = 0.0, distKm = 0.0;
            if (rp.getPickupLocation() != null && rp.getDestinationLocation() != null) {
                FareEstimateRequestDTO fareReq = new FareEstimateRequestDTO();
                fareReq.setPickupLat(rp.getPickupLocation().getY());
                fareReq.setPickupLng(rp.getPickupLocation().getX());
                fareReq.setDestLat(rp.getDestinationLocation().getY());
                fareReq.setDestLng(rp.getDestinationLocation().getX());
                fareReq.setPassengers(1);
                FareEstimateResponseDTO result = fareCalculationService.calculateSharedFare(fareReq);
                fare = result.getPerPassengerFare();
                distKm = result.getDistanceKm();
            }
            rp.setFareShare(fare);
            RideCompletedEvent event = new RideCompletedEvent();
            event.rideId = String.valueOf(rideId);
            event.passengerPhone = rp.getPassenger().getPhoneNumber();
            event.passengerEmail = rp.getPassenger().getEmail() != null
                    ? rp.getPassenger().getEmail() : rp.getPassenger().getPhoneNumber();
            event.fareAmount = fare;
            event.distanceKm = distKm;
            events.add(event);
        }
        ridePassengerRepository.saveAll(confirmed);

        // ── Update route total earnings ──────────────────────────────────────
        // Sum this batch + any already-banked fares from mid-ride DROPPED passengers
        if (ride.getRoute() != null) {
            double droppedTotal = ridePassengerRepository
                    .findByRideAndStatus(ride, RidePassengerStatus.DROPPED)
                    .stream()
                    .mapToDouble(rp -> rp.getFareShare() != null ? rp.getFareShare() : 0.0)
                    .sum();
            double confirmedTotal = events.stream()
                    .mapToDouble(e -> e.fareAmount)
                    .sum();
            Route route = ride.getRoute();
            route.setTotalEarnings(confirmedTotal + droppedTotal);
            route.setStatus("COMPLETED");
            route.setEndTime(LocalDateTime.now());
            route.setCompletedAt(LocalDateTime.now());
            routeRepository.save(route);
        }

        notificationService.broadcastRideCompleted(rideId, events);
        return toResponse(ride);
    }

    /**
     * Driver drops a confirmed passenger at their destination mid-route.
     * Frees up the seat so new riders can join. Does NOT auto-complete the ride.
     */
    @Transactional
    public void dropPassenger(Long rideId, Long passengerId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("You are not the driver of this ride");
        }

        RidePassenger ridePassenger = ridePassengerRepository.findByRide(ride).stream()
                .filter(rp -> rp.getPassenger().getId().equals(passengerId)).findFirst()
                .orElseThrow(() -> new RuntimeException("Passenger not found on this ride"));

        if (ridePassenger.getStatus() != RidePassengerStatus.CONFIRMED) {
            throw new RuntimeException("Passenger is not currently on board");
        }

        double fare = 0.0, distanceKm = 0.0;
        if (ridePassenger.getPickupLocation() != null && ridePassenger.getDestinationLocation() != null) {
            FareEstimateRequestDTO fareReq = new FareEstimateRequestDTO();
            fareReq.setPickupLat(ridePassenger.getPickupLocation().getY());
            fareReq.setPickupLng(ridePassenger.getPickupLocation().getX());
            fareReq.setDestLat(ridePassenger.getDestinationLocation().getY());
            fareReq.setDestLng(ridePassenger.getDestinationLocation().getX());
            fareReq.setPassengers(1);
            FareEstimateResponseDTO fareResult = fareCalculationService.calculateSharedFare(fareReq);
            fare = fareResult.getPerPassengerFare();
            distanceKm = fareResult.getDistanceKm();
        }
        ridePassenger.setStatus(RidePassengerStatus.DROPPED);
        ridePassenger.setFareShare(fare);
        ridePassengerRepository.save(ridePassenger);

        if (ride.getRoute() != null) {
            Route route = ride.getRoute();
            route.setAvailableSeats(route.getAvailableSeats() + 1);
            routeRepository.save(route);
        }

        DropPassengerEvent event = new DropPassengerEvent();
        event.rideId = String.valueOf(rideId);
        event.passengerPhone = ridePassenger.getPassenger().getPhoneNumber();
        event.passengerEmail = ridePassenger.getPassenger().getEmail() != null
                ? ridePassenger.getPassenger().getEmail() : ridePassenger.getPassenger().getPhoneNumber();
        event.fareAmount = fare;
        event.distanceKm = distanceKm;
        notificationService.notifyPassengerDropped(rideId, event);
    }

    /** Rider requests drop-off — notifies driver via WebSocket. */
    @Transactional(readOnly = true)
    /** Driver confirms cash payment received from a specific passenger. */
    public void confirmCashPayment(Long rideId, Long passengerId) {
        User driver = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));
        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("You are not the driver of this ride");
        }
        RidePassenger rp = ridePassengerRepository.findByRide(ride).stream()
                .filter(p -> p.getPassenger().getId().equals(passengerId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Passenger not found on this ride"));
        rp.setCashConfirmed(true);
        ridePassengerRepository.save(rp);
    }

    public void requestDropOff(Long rideId) {
        User passenger = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() != RideStatus.STARTED) {
            throw new RuntimeException("Ride is not in progress");
        }

        ridePassengerRepository.findByRideAndPassenger(ride, passenger)
                .orElseThrow(() -> new RuntimeException("You are not on this ride"));

        // Notify driver to drop this passenger at the next stop
        if (ride.getDriver() != null) {
            NewRiderRequestEvent event = new NewRiderRequestEvent();
            event.type = "DROP_OFF_REQUESTED";
            event.rideId = String.valueOf(rideId);
            event.riderId = String.valueOf(passenger.getId());
            event.passengerPhone = passenger.getPhoneNumber();
            // Re-use the driver request channel with a DROP_OFF_REQUESTED type signal
            // Driver app handles type = "DROP_OFF_REQUESTED"
            notificationService.notifyDriverNewRequest(ride.getDriver().getId(), event);
        }
    }

    /** Rider rates the driver after a ride. */
    @Transactional
    public void rateDriver(Long rideId, int rating) {
        User passenger = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new RuntimeException("Can only rate a completed ride");
        }

        if (ride.getDriver() == null) {
            throw new RuntimeException("No driver on this ride");
        }

        DriverProfile profile = driverProfileRepository.findByUser(ride.getDriver())
                .orElseThrow(() -> new RuntimeException("Driver profile not found"));

        // Running average (handle null for legacy rows)
        int currentCount = profile.getRatingCount() != null ? profile.getRatingCount() : 0;
        double currentAvg = profile.getAverageRating() != null ? profile.getAverageRating() : 0.0;
        int newCount = currentCount + 1;
        double newAvg = ((currentAvg * currentCount) + rating) / newCount;
        profile.setRatingCount(newCount);
        profile.setAverageRating(newAvg);
        driverProfileRepository.save(profile);
    }

    /**
     * Returns all passengers on a ride (for driver view).
     * Includes PENDING, CONFIRMED, and DROPPED — excludes LEFT/REJECTED.
     */
    @Transactional(readOnly = true)
    public List<PassengerInfoDTO> getRidePassengers(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        return ridePassengerRepository.findByRide(ride)
                .stream()
                .filter(rp -> rp.getStatus() != RidePassengerStatus.LEFT
                           && rp.getStatus() != RidePassengerStatus.REJECTED)
                .map(rp -> {
                    String name = passengerProfileRepository
                            .findByUser(rp.getPassenger())
                            .map(PassengerProfile::getFullName)
                            .orElse(rp.getPassenger().getPhoneNumber());
                    return new PassengerInfoDTO(
                            rp.getPassenger().getId(),
                            name,
                            rp.getPassenger().getPhoneNumber(),
                            rp.getPassenger().getEmail(),
                            rp.getStatus().name(),
                            rp.getFareShare()
                    );
                })
                .collect(Collectors.toList());
    }

    private RideResponseDTO toResponse(Ride ride) {
        List<String> passengerPhones = ridePassengerRepository
                .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED)
                .stream()
                .map(rp -> rp.getPassenger().getPhoneNumber())
                .collect(Collectors.toList());

        // Pickup / destination: prefer ride fields, fall back to route addresses
        String pickup = ride.getPickupLocation();
        String dest = ride.getDestination();
        if ((pickup == null || pickup.isBlank()) && ride.getRoute() != null) {
            pickup = ride.getRoute().getOriginAddress();
        }
        if ((dest == null || dest.isBlank()) && ride.getRoute() != null) {
            dest = ride.getRoute().getDestinationAddress();
        }

        // Try to get the current passenger's status + fare + own location names (best-effort)
        String passengerStatus = null;
        Double fareShare = null;
        String passengerPickup = null;
        String passengerDest = null;
        try {
            String username = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
            java.util.Optional<com.backend.iniato.entity.User> userOpt =
                    userRepository.findByPhoneNumber(username)
                            .or(() -> userRepository.findByEmail(username));
            if (userOpt.isPresent()) {
                java.util.Optional<RidePassenger> rpOpt =
                        ridePassengerRepository.findByRideAndPassenger(ride, userOpt.get());
                if (rpOpt.isPresent()) {
                    RidePassenger rp = rpOpt.get();
                    passengerStatus = rp.getStatus().name();
                    fareShare = rp.getFareShare();
                    // Pull the human-readable location names from the originating RideRequest
                    if (rp.getRideRequest() != null) {
                        passengerPickup = rp.getRideRequest().getPickupLocationName();
                        passengerDest   = rp.getRideRequest().getDestinationName();
                    }
                }
            }
        } catch (Exception ignored) {}

        return RideResponseDTO.builder()
                .rideId(ride.getId())
                .driverEmail(ride.getDriver() != null ? ride.getDriver().getEmail() : null)
                .driverPhone(ride.getDriver() != null ? ride.getDriver().getPhoneNumber() : null)
                .pickupLocation(pickup)
                .destination(dest)
                .passengerPickup(passengerPickup)
                .passengerDest(passengerDest)
                .requestedTime(ride.getRequestedTime())
                .status(ride.getStatus())
                .passengerStatus(passengerStatus)
                .fareShare(fareShare)
                .passengers(passengerPhones)
                .build();
    }

    /**
     * Passenger cancels their own participation in a POOL_FORMING ride.
     * If the ride was already STARTED the passenger should use requestDropOff instead.
     * If no confirmed passengers remain after leaving, the ride is auto-cancelled.
     */
    @Transactional
    public RideResponseDTO cancelRideParticipation(Long rideId) {
        User passenger = getCurrentUser();
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        // Allow cancel for POOL_FORMING (leave) or stale STARTED rides
        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new RuntimeException("Ride is already " + ride.getStatus().name().toLowerCase());
        }

        RidePassenger ridePassenger = ridePassengerRepository
                .findByRideAndPassenger(ride, passenger)
                .orElseThrow(() -> new RuntimeException("You are not part of this ride"));

        if (ridePassenger.getStatus() == RidePassengerStatus.LEFT
                || ridePassenger.getStatus() == RidePassengerStatus.REJECTED) {
            throw new RuntimeException("You have already left this ride");
        }

        ridePassenger.setStatus(RidePassengerStatus.LEFT);
        ridePassengerRepository.save(ridePassenger);

        // Restore seat
        if (ride.getRoute() != null) {
            ride.getRoute().setAvailableSeats(ride.getRoute().getAvailableSeats() + 1);
            routeRepository.save(ride.getRoute());
        }

        // Auto-cancel ride if no confirmed passengers remain
        boolean noConfirmedLeft = ridePassengerRepository
                .findByRideAndStatus(ride, RidePassengerStatus.CONFIRMED).isEmpty();
        if (noConfirmedLeft && ride.getStatus() != RideStatus.STARTED) {
            ride.setStatus(RideStatus.CANCELLED);
            rideRepository.save(ride);
            if (ride.getDriver() != null) {
                RideCancelledEvent event = new RideCancelledEvent();
                event.rideId = String.valueOf(rideId);
                event.reason = "Passenger cancelled";
                notificationService.notifyDriverRideCancelled(ride.getDriver().getId(), event);
            }
        }

        return toResponse(ride);
    }
}

