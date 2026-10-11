package com.backend.iniato.services;

import com.backend.iniato.dto.FareEstimateRequestDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import com.backend.iniato.enums.VehicleType;
import org.springframework.stereotype.Service;

/**
 * Fare engine for Sambalpur, Odisha.
 * <p>
 * Rates are per-vehicle and live on {@link VehicleType}. The shape is:
 * <pre>
 *   roadKm   = haversine * ROAD_FACTOR
 *   billable = max(0, roadKm - BASE_DISTANCE_KM)
 *   total    = max(minimumFare, baseFare + billable * perKm)
 * </pre>
 * A pool discount applies only when a poolable vehicle actually carries more
 * than one passenger.
 */
@Service
public class FareCalculationService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Supplies the convenience fee shown alongside a quote.
     * <p>
     * Optional so this service stays constructible without a Spring context —
     * the fare bands are pure arithmetic and their tests rely on that. When
     * absent, quotes simply carry a zero fee.
     */
    private final PlatformFeeService platformFeeService;

    /** Fare maths only — quotes carry no fee breakdown. */
    public FareCalculationService() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FareCalculationService(PlatformFeeService platformFeeService) {
        this.platformFeeService = platformFeeService;
    }

    /**
     * Estimate the fare for a trip.
     * <p>
     * {@code request.vehicleType} selects the rate band; an absent or
     * unrecognised value falls back to {@link VehicleType#AUTO} so that older
     * app builds keep working.
     */
    public FareEstimateResponseDTO calculateSharedFare(FareEstimateRequestDTO request) {
        VehicleType vehicle = VehicleType.fromLegacy(request.getVehicleType());

        double straightLineKm = calculateDistance(
                request.getPickupLat(), request.getPickupLng(),
                request.getDestLat(), request.getDestLng()
        );

        // Haversine understates real road distance; Sambalpur routes run ~30% longer.
        double roadKm = straightLineKm * VehicleType.ROAD_FACTOR;

        double billableKm = Math.max(0.0, roadKm - VehicleType.BASE_DISTANCE_KM);
        double totalFare = vehicle.getBaseFare() + (billableKm * vehicle.getPerKm());
        totalFare = Math.max(vehicle.getMinimumFare(), totalFare);

        // Clamp riders to what the vehicle can physically seat — a bike can
        // never pool, so its fare is never divided.
        int passengers = Math.min(
                Math.max(1, request.getPassengers()),
                vehicle.getCapacity());

        if (vehicle.isPoolable() && passengers > 1) {
            totalFare = totalFare * (1 - VehicleType.POOL_DISCOUNT);
        }

        double perPassengerFare = totalFare / passengers;

        // Surface Iniato's share of the quote. It is carved out of the fare,
        // so the rider still pays exactly perPassengerFare.
        double convenienceFee = platformFeeService == null ? 0.0
                : platformFeeService
                        .convenienceFeeFor(java.math.BigDecimal.valueOf(round2(perPassengerFare)))
                        .doubleValue();

        return new FareEstimateResponseDTO(
                round2(roadKm), round2(totalFare), round2(perPassengerFare), convenienceFee);
    }

    /** Great-circle distance in km between two WGS-84 points. */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** Money and distances are surfaced to riders — avoid long float tails. */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
