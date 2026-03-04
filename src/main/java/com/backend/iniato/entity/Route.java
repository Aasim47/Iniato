package com.backend.iniato.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    private UUID id;
    private UUID driverId;
    private Double originLat;
    private Double originLng;
    private Double destinationLat;
    private Double destinationLng;
    private String polyline;
    private Integer totalSeats;
    private Integer availableSeats;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime createdAt;

    public Integer getAvailableSeats() {
        return availableSeats;
    }
    // getters/setters for other fields
}
