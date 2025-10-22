package com.backend.iniato.entity;

import com.backend.iniato.enums.DriverStatus;
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

    @Enumerated(EnumType.STRING)
    private DriverStatus status;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
