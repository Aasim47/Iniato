package com.backend.iniato.controller;

import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.services.DriverLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DriverLocationWebSocketController {

    private final DriverLocationService driverLocationService;

    @Autowired
    public DriverLocationWebSocketController(DriverLocationService driverLocationService) {
        this.driverLocationService = driverLocationService;
    }

    @MessageMapping("/driver/updateLocation")
    public void receiveDriverLocation(DriverLocationUpdateDTO dto) {
        driverLocationService.updateDriverLocation(dto);
    }
}
