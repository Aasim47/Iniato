package com.backend.iniato.services;


import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.entity.DriverLocation;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.DriverLocationRepository;
import com.backend.iniato.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service

public class DriverLocationService {

    private final DriverLocationRepository driverLocationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    @Autowired
    private UserRepository userRepository;

    @Autowired
    public DriverLocationService(DriverLocationRepository driverLocationRepository, SimpMessagingTemplate messagingTemplate) {
        this.driverLocationRepository = driverLocationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void updateDriverLocation(DriverLocationUpdateDTO dto) {
        Point point = geometryFactory.createPoint(new Coordinate(dto.getLongitude(), dto.getLatitude()));

        User driver = userRepository.findById(dto.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        DriverLocation driverLocation = driverLocationRepository
                .findByDriverId(dto.getDriverId())
                .orElse(DriverLocation.builder()
                        .driver(driver)
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
