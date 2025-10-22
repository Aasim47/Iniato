package com.backend.iniato.services;


import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.entity.DriverLocation;
import com.backend.iniato.repo.DriverLocationRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DriverLocationService {

    private final DriverLocationRepository driverLocationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public void updateDriverLocation(DriverLocationUpdateDTO dto) {
        Point point = geometryFactory.createPoint(new Coordinate(dto.getLongitude(), dto.getLatitude()));

        // find or create driver location record
        DriverLocation driverLocation = driverLocationRepository
                .findByDriverId(dto.getDriverId())
                .orElse(DriverLocation.builder()
                        .driver(null) // optional: fetch driver entity here
                        .build());

        driverLocation.setCurrentLocation(point);
        driverLocation.setOnline(true);
        driverLocation.setLastUpdated(LocalDateTime.now());

        driverLocationRepository.save(driverLocation);

        // Broadcast to passengers if driver has active ride
        if (dto.getRideId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/ride/" + dto.getRideId(),
                    new com.backend.iniato.dto.DriverLocationBroadcastDTO(
                            dto.getDriverId(),
                            dto.getLatitude(),
                            dto.getLongitude(),
                            LocalDateTime.now().toString()
                    )
            );
        }
    }
}
