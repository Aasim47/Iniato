package com.backend.iniato.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    private UUID id;
    private UUID routeId;
    private UUID riderId;
    private UUID pickupStopId;
    private UUID dropStopId;
    private Double fare;
    private String status;
    private LocalDateTime joinedAt;
    // getters/setters
}
