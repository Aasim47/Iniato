package com.backend.iniato.dto;


import com.backend.iniato.enums.RideStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Minimal ride info used for displaying matching shared rides.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideSummaryDTO {
    private Long rideId;
    private String pickupLocation;
    private String destination;
    private RideStatus status;
    private LocalDateTime requestedTime;
    private String driverName;
    private List<String> passengerNames;
}

