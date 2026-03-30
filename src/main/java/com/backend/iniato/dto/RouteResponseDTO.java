package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteResponseDTO {
    private Long routeId;
    private Long rideId;
    private String driverPhone;
    private String status;
    private Double originLat;
    private Double originLng;
    private Double destinationLat;
    private Double destinationLng;
    private Integer totalSeats;
    private Integer availableSeats;
}
