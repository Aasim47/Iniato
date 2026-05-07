package com.backend.iniato.dto;

import com.backend.iniato.enums.RideStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RideResponseDTO {
    private Long rideId;
    private String passengerEmail;
    private String driverEmail;
    private String passengerPhone;   // primary identifier
    private String driverPhone;
    private String pickupLocation;
    private String destination;
    /** Rider's own boarding point name — may differ from the route origin when the rider boards mid-route. */
    private String passengerPickup;
    /** Rider's own drop-off point name — may differ from the route destination. */
    private String passengerDest;
    private LocalDateTime requestedTime;
    private RideStatus status;
    private String passengerStatus;  // the current passenger's own status on this ride
    private List<String> passengers;
    private Double fareShare;        // fare for this passenger (set after drop/complete)
}
