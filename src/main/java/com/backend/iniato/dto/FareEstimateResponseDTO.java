package com.backend.iniato.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FareEstimateResponseDTO {
    private double distanceKm;
    private double totalFare;
    private double perPassengerFare;
}
