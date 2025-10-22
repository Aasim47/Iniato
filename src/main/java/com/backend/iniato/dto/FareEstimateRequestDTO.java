package com.backend.iniato.dto;

import lombok.Data;

@Data
public class FareEstimateRequestDTO {
    private double pickupLat;
    private double pickupLng;
    private double destLat;
    private double destLng;
    private int passengers;
}