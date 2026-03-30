package com.backend.iniato.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class DriverLocationBroadcastDTO {
    private Long driverId;
    private double latitude;
    private double longitude;
    private String timestamp;

    public DriverLocationBroadcastDTO(Long driverId, double latitude, double longitude, String timestamp) {
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }
}