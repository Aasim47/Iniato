package com.backend.iniato.entity;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {
    @Id
    private UUID userId;
    private String vehicleType;
    private String vehicleNumber;
    private Boolean isOnline;
    private Double currentLat;
    private Double currentLng;
    // getters/setters
}

