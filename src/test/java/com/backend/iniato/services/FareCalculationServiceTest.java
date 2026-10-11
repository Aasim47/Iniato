package com.backend.iniato.services;

import com.backend.iniato.dto.FareEstimateRequestDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import com.backend.iniato.enums.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the Sambalpur fare bands. These are the numbers quoted to the
 * business, so a rate change should fail here loudly rather than silently
 * repricing every ride.
 */
class FareCalculationServiceTest {

    private final FareCalculationService service = new FareCalculationService();

    /**
     * Two points ~5 km apart in Sambalpur (Ainthapali → Burla, roughly).
     * Road-adjusted by ROAD_FACTOR to 6.5 km, of which 2 km is in the base.
     */
    private static final double PICKUP_LAT = 21.4669;
    private static final double PICKUP_LNG = 83.9812;
    private static final double DEST_LAT = 21.5100;
    private static final double DEST_LNG = 83.9900;

    private FareEstimateResponseDTO quote(VehicleType type, int passengers) {
        FareEstimateRequestDTO req = new FareEstimateRequestDTO();
        req.setPickupLat(PICKUP_LAT);
        req.setPickupLng(PICKUP_LNG);
        req.setDestLat(DEST_LAT);
        req.setDestLng(DEST_LNG);
        req.setPassengers(passengers);
        req.setVehicleType(type.name());
        return service.calculateSharedFare(req);
    }

    @Test
    @DisplayName("~5 km solo trip prices per the published Sambalpur bands")
    void soloFaresMatchPublishedBands() {
        // Straight-line ~4.93 km → ~6.4 km road → ~4.4 billable km.
        assertEquals(51.0, quote(VehicleType.BIKE, 1).getTotalFare(), 2.0);
        assertEquals(74.0, quote(VehicleType.AUTO, 1).getTotalFare(), 2.0);
        assertEquals(116.0, quote(VehicleType.CAB, 1).getTotalFare(), 2.5);
    }

    @Test
    @DisplayName("A full auto splits to roughly the local shared-tempo rate")
    void pooledAutoLandsNearLocalSharedRate() {
        FareEstimateResponseDTO pooled = quote(VehicleType.AUTO, 3);
        // 15% pool discount, then split three ways — should sit in the
        // ₹10–25/head range Sambalpur riders actually pay.
        assertTrue(pooled.getPerPassengerFare() >= 10.0 && pooled.getPerPassengerFare() <= 25.0,
                "expected ₹10–25 per head, got " + pooled.getPerPassengerFare());
        assertTrue(pooled.getTotalFare() < quote(VehicleType.AUTO, 1).getTotalFare(),
                "pooling must be cheaper in total than riding solo");
    }

    @Test
    @DisplayName("A bike never pools, so its fare is never divided")
    void bikeFareIsNeverSplit() {
        // Even if a client asks for 3 riders, a bike seats 1.
        FareEstimateResponseDTO bike = quote(VehicleType.BIKE, 3);
        assertEquals(bike.getTotalFare(), bike.getPerPassengerFare(), 0.001);
    }

    @Test
    @DisplayName("Very short trips fall back to the minimum fare")
    void minimumFareApplies() {
        FareEstimateRequestDTO req = new FareEstimateRequestDTO();
        req.setPickupLat(PICKUP_LAT);
        req.setPickupLng(PICKUP_LNG);
        req.setDestLat(PICKUP_LAT + 0.0005); // ~55 m
        req.setDestLng(PICKUP_LNG);
        req.setPassengers(1);

        req.setVehicleType("BIKE");
        assertEquals(VehicleType.BIKE.getMinimumFare(),
                service.calculateSharedFare(req).getTotalFare(), 0.001);

        req.setVehicleType("CAB");
        assertEquals(VehicleType.CAB.getMinimumFare(),
                service.calculateSharedFare(req).getTotalFare(), 0.001);
    }

    @Test
    @DisplayName("Legacy and absent vehicle types degrade to AUTO, not to a crash")
    void legacyVehicleTypesResolve() {
        double auto = quote(VehicleType.AUTO, 1).getTotalFare();

        FareEstimateRequestDTO req = new FareEstimateRequestDTO();
        req.setPickupLat(PICKUP_LAT);
        req.setPickupLng(PICKUP_LNG);
        req.setDestLat(DEST_LAT);
        req.setDestLng(DEST_LNG);
        req.setPassengers(1);

        req.setVehicleType(null);
        assertEquals(auto, service.calculateSharedFare(req).getTotalFare(), 0.001);

        req.setVehicleType("AUTO_RICKSHAW");
        assertEquals(auto, service.calculateSharedFare(req).getTotalFare(), 0.001);

        req.setVehicleType("CAR");
        assertEquals(quote(VehicleType.CAB, 1).getTotalFare(),
                service.calculateSharedFare(req).getTotalFare(), 0.001);
    }

    @Test
    @DisplayName("Capacities are the ones the business agreed")
    void capacitiesAreLocked() {
        assertEquals(1, VehicleType.BIKE.getCapacity());
        assertEquals(3, VehicleType.AUTO.getCapacity());
        assertEquals(4, VehicleType.CAB.getCapacity());
    }
}
