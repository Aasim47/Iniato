package com.backend.iniato.dto;


import com.backend.iniato.entity.Ride;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO returned when a passenger searches for shared rides.
 * It includes matching ongoing rides and available nearby drivers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideMatchResponseDTO {

    /**
     * List of existing rides that match the requested route
     * (same direction, within proximity radius).
     */
    private List<RideSummaryDTO> matchingRides;

    /**
     * Nearby drivers who are free and can host a new shared ride.
     */
    private List<NearbyDriverDTO> nearbyDrivers;
}

