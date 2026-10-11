package com.backend.iniato.dto;

import java.time.LocalDateTime;
import com.backend.iniato.enums.RideStatus;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectRideResponseDTO {
    private Long id;
    private Long driverId;
    private Long passengerId;
    private Double pickupLat;
    private Double pickupLng;
    private Double destinationLat;
    private Double destinationLng;
    private String pickupLocationName;
    private String destinationName;
    private LocalDateTime requestedTime;
    private LocalDateTime acceptedTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private RideStatus status;
    private Double estimatedFare;
    private String vehicleType;
    private String vehicleNumber;
    private Integer passengerCount;
    private String passengerName;
    private String driverName;
    private Double driverRating;

    /**
     * Iniato's fee, already included in {@link #estimatedFare}.
     * Populated once the ride is settled; null before that.
     */
    private Double convenienceFee;
}