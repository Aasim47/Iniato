package com.backend.iniato.entity;

import com.backend.iniato.enums.RidePassengerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

/**
 * Explicit join entity for the ride_passengers table.
 * Replaces the implicit @ManyToMany between Ride and User,
 * adding per-passenger metadata: pickup/drop points, status,
 * fare share, and a back-link to the originating RideRequest.
 */
@Entity
@Table(
    name = "ride_passengers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ride_id", "user_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RidePassenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User passenger;

    /** PostGIS point — where this passenger boards the ride. */
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point pickupLocation;

    /** PostGIS point — where this passenger exits the ride. */
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point destinationLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RidePassengerStatus status;

    /** When the driver accepted and the passenger was added to the ride. */
    private LocalDateTime joinedAt;

    /**
     * The RideRequest that originated this booking.
     * Kept for audit and for retrieving original pickup/dest coords.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_request_id")
    private RideRequest rideRequest;

    /**
     * This passenger's share of the total fare.
     * Populated when the ride is completed and fare is split.
     */
    private Double fareShare;

    /** True once the driver confirms cash payment was received for this passenger. */
    @Builder.Default
    private boolean cashConfirmed = false;
}
