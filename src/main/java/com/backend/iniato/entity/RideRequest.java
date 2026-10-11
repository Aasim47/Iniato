package com.backend.iniato.entity;


import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "ride_requests",
    // (status, pickup_time) serves both findPendingNear — which narrows on
    // these two before the PostGIS distance test — and the scheduled purge that
    // deletes stale rows. The passenger index covers findByPassenger.
    //
    // The column is passenger_user_id, not passenger_id: neither association
    // below declares a @JoinColumn, so Hibernate derives the name as
    // <field>_<referenced PK column>, and User's id column is named user_id.
    // matched_ride_id follows the same rule (Ride's PK column is plain "id").
    indexes = {
        @Index(name = "ix_ride_requests_status_time", columnList = "status, pickup_time"),
        @Index(name = "ix_ride_requests_passenger", columnList = "passenger_user_id"),
        @Index(name = "ix_ride_requests_matched_ride", columnList = "matched_ride_id")
    }
)
public class RideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User passenger;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point pickupLocation;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point destinationLocation;

    /** Human-readable name of the passenger's own boarding point (e.g. "Sambalpur Market"). */
    private String pickupLocationName;

    /** Human-readable name of the passenger's own drop-off point. */
    private String destinationName;

    private LocalDateTime pickupTime;

    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    private Ride matchedRide;


}
