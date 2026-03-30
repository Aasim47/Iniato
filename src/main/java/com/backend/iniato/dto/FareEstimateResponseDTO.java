package com.backend.iniato.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data

public class FareEstimateResponseDTO {
    private double distanceKm;
    private double totalFare;

    public FareEstimateResponseDTO(double distanceKm, double totalFare, double perPassengerFare) {
        this.distanceKm = distanceKm;
        this.totalFare = totalFare;
        this.perPassengerFare = perPassengerFare;
    }

    private double perPassengerFare;
}
