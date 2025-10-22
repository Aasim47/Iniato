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
@Table(name = "driver_locations")
public class DriverLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private User driver;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point currentLocation;

    private boolean online;

    private LocalDateTime lastUpdated;

}
