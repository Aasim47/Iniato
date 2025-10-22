package com.backend.iniato.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DriverLocationBroadcastDTO {
    private Long driverId;
    private double latitude;
    private double longitude;
    private String timestamp;
}