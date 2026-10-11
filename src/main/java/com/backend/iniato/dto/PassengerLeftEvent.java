package com.backend.iniato.dto;

/**
 * Broadcast on the ride topic when a passenger voluntarily leaves a pool
 * before the ride starts. Used only to refresh the driver's passenger list —
 * it is NOT a drop-off, so it must never trigger fare/rating on the rider side.
 * Rider clients ignore this type.
 */
public class PassengerLeftEvent {
    public String type = "PASSENGER_LEFT";
    public String rideId;
    public String passengerPhone;
}
