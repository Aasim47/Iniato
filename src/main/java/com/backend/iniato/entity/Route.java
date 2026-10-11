package com.backend.iniato.entity;

import com.backend.iniato.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "routes",
    // findActiveRoutesNear filters on status and available_seats before the
    // spatial test, and findByDriver / findByDriverAndStatus drive the driver's
    // own route list. Both scanned the table until now.
    indexes = {
        @Index(name = "ix_routes_status_seats", columnList = "status, available_seats"),
        @Index(name = "ix_routes_driver_status", columnList = "driver_id, status")
    }
)
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
    private String originAddress;       // human-readable start name
    private String destinationAddress;  // human-readable end name
    private String polyline;

    /**
     * Vehicle class serving this route, copied from the driver's profile at
     * creation time. Determines {@link #totalSeats} — see
     * {@code RouteService.createRoute}.
     */
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    private Integer totalSeats;
    private Integer availableSeats;
    private String status; // ACTIVE, COMPLETED, CANCELLED
    private LocalDateTime startTime;
    private LocalDateTime endTime;       // when the route was completed/cancelled
    private LocalDateTime completedAt;   // alias kept for DTO compatibility
    private LocalDateTime createdAt;
    private Double totalEarnings;        // sum of all fareShare amounts for this route

    // Optimistic-lock guard. Complements the pessimistic lock on the accept
    // path and makes the "concurrent modification" handling in
    // RouteService.cancelRoute/completeRoute actually meaningful.
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
