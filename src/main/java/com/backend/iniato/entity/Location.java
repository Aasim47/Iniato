package com.backend.iniato.entity;

import jakarta.persistence.Embeddable;
import lombok.*;
import org.locationtech.jts.geom.Point;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Location {
    private double latitude;
    private double longitude;
}