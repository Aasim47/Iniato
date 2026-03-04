package com.backend.iniato.entity;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "route_stops")
public class RouteStop {
    @Id
    private UUID id;
    private UUID routeId;
    private Double lat;
    private Double lng;
    private String type; // pickup/drop
    private Integer sequenceOrder;
    // getters/setters
}

