package com.backend.iniato.dto;

public class RouteCreateRequest {
    public String driverId;
    public double originLat;
    public double originLng;
    public double destinationLat;
    public double destinationLng;
    public int totalSeats;
    public String originAddress;      // human-readable origin name (optional)
    public String destinationAddress; // human-readable destination name (optional)
}
