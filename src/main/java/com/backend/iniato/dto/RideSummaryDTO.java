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
 * Only rides whose direction is compatible with the passenger's
 * requested route are ever included in the response.
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
    private Integer availableSeats;

    /** Route origin coordinates (driver's starting point). */
    private Double originLat;
    private Double originLng;

    /** Route destination coordinates (driver's endpoint). */
    private Double destinationLat;
    private Double destinationLng;

    private List<String> passengerNames;
}

