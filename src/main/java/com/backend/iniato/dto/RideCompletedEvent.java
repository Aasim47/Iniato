package com.backend.iniato.dto;
public class RideCompletedEvent {
    public String type = "RIDE_COMPLETED";
    public String rideId;
    public String passengerPhone; // primary identifier
    public String passengerEmail; // kept for backward compat
    public double fareAmount;
    public double distanceKm;
}
