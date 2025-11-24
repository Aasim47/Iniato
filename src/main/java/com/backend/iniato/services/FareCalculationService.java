package com.backend.iniato.services;

import com.backend.iniato.dto.FareEstimateRequestDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class FareCalculationService {

    private static final double BASE_FARE = 20.0;
    private static final double PRICE_PER_KM = 10.0;
    private static final double POOL_DISCOUNT = 0.15; // 15% discount for pooling

    public FareEstimateResponseDTO calculateSharedFare(FareEstimateRequestDTO request) {
        double distanceKm = calculateDistance(
                request.getPickupLat(), request.getPickupLng(),
                request.getDestLat(), request.getDestLng()
        );

        double totalFare = BASE_FARE + (distanceKm * PRICE_PER_KM);
        double discountedFare = totalFare * (1 - POOL_DISCOUNT);
        double perPassengerFare = discountedFare / request.getPassengers();

        return new FareEstimateResponseDTO(distanceKm, discountedFare, perPassengerFare);
    }

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
}
