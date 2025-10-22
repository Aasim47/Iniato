package com.backend.iniato.dto;
import lombok.Data;

@Data
public class DriverLocationUpdateDTO {
    private Long driverId;
    private double latitude;
    private double longitude;
    private Long rideId;
}