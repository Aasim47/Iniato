package com.backend.iniato.entity;


import com.backend.iniato.enums.RideStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pickupLocation;
    private String destination;
    private LocalDateTime requestedTime;

    @Enumerated(EnumType.STRING)
    private RideStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ride_passengers",
            joinColumns = @JoinColumn(name = "ride_id"),
            inverseJoinColumns = @JoinColumn(name = "passenger_id")
    )
    @Builder.Default
    private List<User> passengers = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private User driver;

    private LocalDateTime acceptedTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
