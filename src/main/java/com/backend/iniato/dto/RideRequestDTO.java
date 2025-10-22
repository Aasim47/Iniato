package com.backend.iniato.dto;
import lombok.Data;

@Data
public class RideRequestDTO {
    private double pickupLat;
    private double pickupLng;
    private double destLat;
    private double destLng;
    private String pickupTime;
}