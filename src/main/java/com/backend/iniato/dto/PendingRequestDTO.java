package com.backend.iniato.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Clean representation of a pending rider join-request for the driver view.
 * Uses explicit lat/lng doubles instead of JTS {@code Point} so it serializes
 * predictably to JSON and matches the real-time WebSocket event shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingRequestDTO {
    private Long requestId;
    private Long rideId;
    private Long riderId;
    private String riderName;
    private String riderPhone;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private String pickupName;
    private String destName;
    private String status;
}

