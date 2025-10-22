package com.backend.iniato.controller;


import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.services.DriverLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DriverLocationWebSocketController {

    private final DriverLocationService driverLocationService;

    @MessageMapping("/driver/updateLocation")
    public void receiveDriverLocation(DriverLocationUpdateDTO dto) {
        driverLocationService.updateDriverLocation(dto);
    }
}
