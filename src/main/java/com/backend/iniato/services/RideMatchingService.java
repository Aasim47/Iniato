package com.backend.iniato.services;

import com.backend.iniato.dto.NearbyDriverDTO;
import com.backend.iniato.dto.RideMatchResponseDTO;
import com.backend.iniato.dto.RideRequestDTO;
import com.backend.iniato.dto.RideSummaryDTO;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.enums.RideStatus;
import com.backend.iniato.repo.DriverLocationRepository;
import com.backend.iniato.repo.RideRepository;
import com.backend.iniato.repo.RideRequestRepository;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RideMatchingService {

    private static final int SRID = 4326;

    /**
     * Maximum deviation angle (degrees) between the route direction vector and the
     * passenger's travel direction vector. Rides with a larger angular difference
     * are travelling in a different / opposite direction and should be excluded.
     * 60° lets passengers whose route is a reasonable sub-path of the driver's
     * route join while excluding opposite-direction rides.
     */
    private static final double MAX_DIRECTION_ANGLE_DEG = 60.0;

    /**
     * Maximum perpendicular distance (metres) a passenger's pickup point can be
     * from the driver's route straight-line corridor before being excluded.
     */
    private static final double MAX_CORRIDOR_DEVIATION_METERS = 1500.0;

    /**
     * Metres per degree of latitude (approximate, valid for ±60° latitude).
     */
    private static final double METERS_PER_DEGREE_LAT = 111_000.0;

    private final RideRequestRepository rideRequestRepository;
    private final DriverLocationRepository driverLocationRepository;
    private final RideRepository rideRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    @Autowired
    public RideMatchingService(RideRequestRepository rideRequestRepository,
                               DriverLocationRepository driverLocationRepository,
                               RideRepository rideRepository) {
        this.rideRequestRepository = rideRequestRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.rideRepository = rideRepository;
    }

    public RideRequest saveRideRequest(RideRequestDTO requestDTO, User passenger) {
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
                .pickupTime(java.time.LocalDateTime.parse(requestDTO.getPickupTime()))
                .status("REQUESTED")
                .build();

        return rideRequestRepository.save(rideRequest);
    }

    /**
     * Find active POOL_FORMING rides and nearby drivers for a given pickup/destination.
     *
     * <p>A ride is included only when ALL of the following hold:
     * <ol>
     *   <li>The ride is in POOL_FORMING status and has seats available.</li>
     *   <li>The ride has an associated {@link Route} with coordinate data.</li>
     *   <li>The passenger's pickup point is within {@value MAX_CORRIDOR_DEVIATION_METERS} m
     *       of the straight-line corridor between the route's origin and destination.</li>
     *   <li>The angular difference between the route direction vector and the passenger's
     *       travel direction vector is less than {@value MAX_DIRECTION_ANGLE_DEG}°
     *       — this eliminates rides travelling in the opposite direction.</li>
     *   <li>The passenger's pickup is not <em>behind</em> the route origin (the driver
     *       has already passed or is not heading toward the pickup).</li>
     * </ol>
     */
    public RideMatchResponseDTO findSharedRideMatches(RideRequestDTO requestDTO) {
        Point pickup = geometryFactory.createPoint(
                new Coordinate(requestDTO.getPickupLng(), requestDTO.getPickupLat()));
        pickup.setSRID(SRID);

        double radiusMeters = 2000; // 2 km radius for nearby drivers

        List<Ride> allPoolFormingRides = rideRepository.findByStatus(RideStatus.POOL_FORMING);
        List<DriverLocation> drivers = driverLocationRepository.findNearbyDrivers(
                requestDTO.getPickupLng(), requestDTO.getPickupLat(), radiusMeters);

        List<RideSummaryDTO> rideSummaries = allPoolFormingRides.stream()
                .filter(ride -> isRideCompatibleWithPassenger(ride, requestDTO))
                .map(r -> {
                    Route route = r.getRoute();
                    return RideSummaryDTO.builder()
                            .rideId(r.getId())
                            .pickupLocation(r.getPickupLocation())
                            .destination(r.getDestination())
                            .status(r.getStatus())
                            .requestedTime(r.getRequestedTime())
                            .driverName(r.getDriver() != null ? r.getDriver().getPhoneNumber() : "Unassigned")
                            .availableSeats(route != null ? route.getAvailableSeats() : null)
                            .originLat(route != null ? route.getOriginLat() : null)
                            .originLng(route != null ? route.getOriginLng() : null)
                            .destinationLat(route != null ? route.getDestinationLat() : null)
                            .destinationLng(route != null ? route.getDestinationLng() : null)
                            .passengerNames(r.getPassengers().stream()
                                    .filter(rp -> rp.getStatus() == RidePassengerStatus.CONFIRMED)
                                    .map(rp -> rp.getPassenger().getPhoneNumber())
                                    .toList())
                            .build();
                })
                .toList();

        List<NearbyDriverDTO> nearbyDriverDTOs = drivers.stream()
                .filter(d -> d.getDriver() != null)
                .map(d -> new NearbyDriverDTO(
                        d.getDriver().getId(),
                        d.getCurrentLocation().getY(),
                        d.getCurrentLocation().getX(),
                        pickup.distance(d.getCurrentLocation()) * 111000
                ))
                .toList();

        return RideMatchResponseDTO.builder()
                .matchingRides(rideSummaries)
                .nearbyDrivers(nearbyDriverDTOs)
                .build();
    }

    // -----------------------------------------------------------------------
    // Direction & corridor compatibility helpers
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} only when the passenger's pickup/destination is
     * directionally compatible with the given ride's route.
     */
    private boolean isRideCompatibleWithPassenger(Ride ride, RideRequestDTO passenger) {
        Route route = ride.getRoute();

        // If there is no route geometry we cannot validate direction — exclude to be safe.
        if (route == null
                || route.getOriginLat() == null || route.getOriginLng() == null
                || route.getDestinationLat() == null || route.getDestinationLng() == null) {
            return false;
        }

        // No seats left — skip
        if (ride.getRoute().getAvailableSeats() != null && ride.getRoute().getAvailableSeats() <= 0) {
            return false;
        }

        double rOriginLat  = route.getOriginLat();
        double rOriginLng  = route.getOriginLng();
        double rDestLat    = route.getDestinationLat();
        double rDestLng    = route.getDestinationLng();

        double pPickupLat  = passenger.getPickupLat();
        double pPickupLng  = passenger.getPickupLng();
        double pDestLat    = passenger.getDestLat();
        double pDestLng    = passenger.getDestLng();

        // 1. Direction-vector dot product check
        //    Route vector: R = (rDestLat - rOriginLat, rDestLng - rOriginLng)
        //    Passenger vector: P = (pDestLat - pPickupLat, pDestLng - pPickupLng)
        //    We scale by cos(lat) to compensate for longitude compression.
        double cosLat = Math.cos(Math.toRadians((rOriginLat + rDestLat) / 2.0));

        double rVecLat = rDestLat - rOriginLat;
        double rVecLng = (rDestLng - rOriginLng) * cosLat;

        double pVecLat = pDestLat - pPickupLat;
        double pVecLng = (pDestLng - pPickupLng) * cosLat;

        double dot      = rVecLat * pVecLat + rVecLng * pVecLng;
        double rMag     = Math.sqrt(rVecLat * rVecLat + rVecLng * rVecLng);
        double pMag     = Math.sqrt(pVecLat * pVecLat + pVecLng * pVecLng);

        // If either vector has zero magnitude we cannot determine direction — exclude
        if (rMag < 1e-9 || pMag < 1e-9) {
            return false;
        }

        double cosAngle = dot / (rMag * pMag);
        // Clamp to [-1, 1] to guard against floating-point drift
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        double angleDeg = Math.toDegrees(Math.acos(cosAngle));

        if (angleDeg > MAX_DIRECTION_ANGLE_DEG) {
            // Passenger is heading in a significantly different / opposite direction
            return false;
        }

        // 2. Corridor check — passenger's pickup must be close to the route line segment
        double corridorDeviationMeters = perpendicularDistanceToSegmentMeters(
                pPickupLat, pPickupLng,
                rOriginLat, rOriginLng,
                rDestLat, rDestLng);

        if (corridorDeviationMeters > MAX_CORRIDOR_DEVIATION_METERS) {
            return false;
        }

        // 3. Pickup-ahead check — passenger's pickup projection along the route vector
        //    must be between 0 and 1 (i.e. the pickup lies between origin and destination,
        //    not behind the driver or past the destination).
        double t = projectionParameter(
                pPickupLat, pPickupLng,
                rOriginLat, rOriginLng,
                rDestLat, rDestLng);

        // Allow a small overshoot (-0.1) so that pickups slightly behind the origin
        // (e.g. at a stop just before departure) are still included.
        // t > 1.0 would mean the pickup is beyond the route destination — exclude.
        if (t < -0.1 || t > 1.0) {
            return false;
        }

        return true;
    }

    /**
     * Computes the perpendicular distance (in metres) from point P to the
     * line segment AB using the cross-product formula.
     */
    private double perpendicularDistanceToSegmentMeters(
            double pLat, double pLng,
            double aLat, double aLng,
            double bLat, double bLng) {

        double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));

        // Convert to a flat-earth Cartesian space (metres)
        double ax = aLng * cosLat * METERS_PER_DEGREE_LAT;
        double ay = aLat * METERS_PER_DEGREE_LAT;
        double bx = bLng * cosLat * METERS_PER_DEGREE_LAT;
        double by = bLat * METERS_PER_DEGREE_LAT;
        double px = pLng * cosLat * METERS_PER_DEGREE_LAT;
        double py = pLat * METERS_PER_DEGREE_LAT;

        double abx = bx - ax;
        double aby = by - ay;
        double segLenSq = abx * abx + aby * aby;

        if (segLenSq < 1e-9) {
            // Degenerate segment (origin == destination) — fall back to point distance
            double dx = px - ax;
            double dy = py - ay;
            return Math.sqrt(dx * dx + dy * dy);
        }

        // Cross product |AB × AP| / |AB| gives perpendicular distance
        double cross = Math.abs(abx * (ay - py) - aby * (ax - px));
        return cross / Math.sqrt(segLenSq);
    }

    /**
     * Returns the scalar projection parameter t of point P onto segment AB.
     * t = 0 → P projects onto A; t = 1 → P projects onto B.
     * Values outside [0,1] indicate P projects outside the segment.
     */
    private double projectionParameter(
            double pLat, double pLng,
            double aLat, double aLng,
            double bLat, double bLng) {

        double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));

        double ax = aLng * cosLat;
        double ay = aLat;
        double bx = bLng * cosLat;
        double by = bLat;
        double px = pLng * cosLat;
        double py = pLat;

        double abx = bx - ax;
        double aby = by - ay;
        double apx = px - ax;
        double apy = py - ay;

        double segLenSq = abx * abx + aby * aby;
        if (segLenSq < 1e-9) return 0.0;

        return (apx * abx + apy * aby) / segLenSq;
    }
}
