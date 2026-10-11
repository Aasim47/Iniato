package com.backend.iniato.entity;

import com.backend.iniato.enums.DriverStatus;
import com.backend.iniato.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private String phoneNumber;
    private String vehicleRegistration;
    private String licenseNumber;
    /**
     * Vehicle this driver operates. Determines the seat capacity of every
     * route they create and the fare band applied to it.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VehicleType vehicleType = VehicleType.AUTO;

    @Enumerated(EnumType.STRING)
    private DriverStatus status;

    @Column(columnDefinition = "float8 default 0.0")
    @Builder.Default
    private Double averageRating = 0.0;

    @Column(columnDefinition = "integer default 0")
    @Builder.Default
    private Integer ratingCount = 0;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
