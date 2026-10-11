package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A saved ride request, flattened for the wire.
 *
 * <p>Replaces returning the {@code RideRequest} entity straight from the
 * controller. That serialized the full {@code passenger} User — password hash
 * included — and reached the lazy {@code matchedRide} association while writing
 * the response, which only worked with {@code spring.jpa.open-in-view=true}.
 */
@Data
@Builder
public class RideRequestResponseDTO {

    private Long requestId;
    private Long passengerId;
    private String passengerPhone;

    private Double pickupLat;
    private Double pickupLng;
    private Double destLat;
    private Double destLng;

    private String pickupLocationName;
    private String destinationName;

    private LocalDateTime pickupTime;
    private String status;

    /** Id of the ride this request was matched to, if any. */
    private Long matchedRideId;
}
