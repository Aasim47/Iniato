package com.backend.iniato.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "routes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    private Double originLat;
    private Double originLng;
    private Double destinationLat;
    private Double destinationLng;
    private String polyline;
    private Integer totalSeats;
    private Integer availableSeats;
    private String status; // ACTIVE, COMPLETED, CANCELLED
    private LocalDateTime startTime;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
