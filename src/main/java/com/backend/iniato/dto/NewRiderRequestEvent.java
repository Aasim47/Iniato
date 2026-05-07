package com.backend.iniato.dto;

public class NewRiderRequestEvent {
    public String type = "NEW_RIDER_REQUEST";
    public String rideId;
    public String requestId;
    public String riderId;
    public String passengerPhone;
    public double pickupLat;
    public double pickupLng;
    public double dropLat;
    public double dropLng;
}
