package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

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
    private String originAddress;       // human-readable start name
    private String destinationAddress;  // human-readable end name
    private Integer totalSeats;
    private Integer availableSeats;
    private LocalDateTime completedAt;
    private Double earnings;
}
