package com.backend.iniato.dto;

public class DropPassengerEvent {
    public String type = "PASSENGER_DROPPED";
    public String rideId;
    public String passengerPhone; // primary identifier
    public String passengerEmail; // kept for backward compat
    public double fareAmount;
    public double distanceKm;
}
