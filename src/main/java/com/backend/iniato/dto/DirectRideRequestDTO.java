package com.backend.iniato.dto;

import java.time.LocalDateTime;
import com.backend.iniato.enums.RideStatus;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectRideRequestDTO {
    private double pickupLat;
    private double pickupLng;
    private double destLat;
    private double destLng;
    private String pickupLocationName;
    private String destinationName;
    private String pickupTime;
    private String vehicleType;
    private Integer passengerCount;
}