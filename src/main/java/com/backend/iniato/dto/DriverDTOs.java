package com.backend.iniato.dto;

public class DriverActivateRequest {
    public String driverId;
}

public class DriverSetOnlineRequest {
    public String driverId;
    public boolean online;
}

public class DriverCreateRouteRequest {
    public String driverId;
    public double originLat;
    public double originLng;
    public double destinationLat;
    public double destinationLng;
    public int totalSeats;
}

