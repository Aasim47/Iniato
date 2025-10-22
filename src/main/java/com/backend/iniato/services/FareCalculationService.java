package com.backend.iniato.services;

import com.backend.iniato.dto.FareEstimateRequestDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class FareCalculationService {

    // constants — tweak for your pricing logic
    private static final double BASE_FARE = 20.0; // base fare in INR
    private static final double PRICE_PER_KM = 12.0; // rate per km in INR

    public FareEstimateResponseDTO calculateFare(FareEstimateRequestDTO request) {
        double distanceKm = calculateDistance(
                request.getPickupLat(), request.getPickupLng(),
                request.getDestLat(), request.getDestLng()
        );

        double totalFare = BASE_FARE + (distanceKm * PRICE_PER_KM);
        double perPassengerFare = totalFare / request.getPassengers();

        return new FareEstimateResponseDTO(distanceKm, totalFare, perPassengerFare);
    }

    // Haversine formula
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}