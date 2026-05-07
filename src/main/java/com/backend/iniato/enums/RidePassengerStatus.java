package com.backend.iniato.enums;

public enum RidePassengerStatus {
    PENDING,    // Request submitted, awaiting driver acceptance
    CONFIRMED,  // Driver accepted — passenger is on the ride
    DROPPED,    // Driver dropped this passenger at their destination mid-route
    LEFT,       // Passenger left before ride started
    COMPLETED,  // Ride finished with this passenger aboard
    REJECTED    // Driver rejected the request
}
