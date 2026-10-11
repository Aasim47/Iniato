package com.backend.iniato.dto;
public class RideCompletedEvent {
    public String type = "RIDE_COMPLETED";
    public String rideId;
    public String passengerPhone; // primary identifier
    public String passengerEmail; // kept for backward compat
    /** What the rider pays — the convenience fee is already inside this. */
    public double fareAmount;
    public double distanceKm;
    /** Iniato's share of {@link #fareAmount}, carved out rather than added on. */
    public double convenienceFee;
}
