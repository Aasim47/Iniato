package com.backend.iniato.dto;

public class RouteCreateRequest {
    public String driverId;
    public double originLat;
    public double originLng;
    public double destinationLat;
    public double destinationLng;
    public int totalSeats;
}

public class RouteUpdateLocationRequest {
    public String routeId;
    public double lat;
    public double lng;
}

public class RouteAddStopRequest {
    public String routeId;
    public double lat;
    public double lng;
    public String type;
    public int sequenceOrder;
}

