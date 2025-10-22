package com.backend.iniato.dto;

import com.backend.iniato.enums.RideStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RideResponseDTO {
    private Long rideId;
    private String passengerEmail;
    private String driverEmail;
    private String pickupLocation;
    private String destination;
    private LocalDateTime requestedTime;
    private RideStatus status;
}
