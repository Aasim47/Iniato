package com.backend.iniato.dto;

public class DriverLocationUpdateEvent {
    public String driverId;
    public double lat;
    public double lng;
}

public class NewRiderRequestEvent {
    public String riderId;
    public double pickupLat;
    public double pickupLng;
    public double dropLat;
    public double dropLng;
}

public class PassengerAddedEvent {
    public String rideId;
    public String passengerId;
}

public class RideStartedEvent {
    public String rideId;
}

public class RideCompletedEvent {
    public String rideId;
}

