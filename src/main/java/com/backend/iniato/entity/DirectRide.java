package com.backend.iniato.entity;

import com.backend.iniato.enums.RideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "direct_rides")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectRide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User driver;

    @ManyToOne
    private User passenger;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point pickupLocation;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point destinationLocation;

    private String pickupLocationName;
    private String destinationName;

    private LocalDateTime requestedTime;
    private LocalDateTime acceptedTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private RideStatus status; // REQUESTED, ACCEPTED, STARTED, COMPLETED, CANCELLED

    private Double estimatedFare;
    private String vehicleType;
    private String vehicleNumber;
    private Integer passengerCount;

    private Double pickupLat;
    private Double pickupLng;
    private Double destinationLat;
    private Double destinationLng;
}