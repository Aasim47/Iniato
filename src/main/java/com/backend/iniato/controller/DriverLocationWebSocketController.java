package com.backend.iniato.controller;
import com.backend.iniato.dto.DriverLocationUpdateDTO;
import com.backend.iniato.dto.PassengerLocationBroadcastDTO;
import com.backend.iniato.dto.PassengerLocationUpdateDTO;
import com.backend.iniato.services.DriverLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
@Controller
public class DriverLocationWebSocketController {
    private final DriverLocationService driverLocationService;
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    public DriverLocationWebSocketController(DriverLocationService driverLocationService,
                                              SimpMessagingTemplate messagingTemplate) {
        this.driverLocationService = driverLocationService;
        this.messagingTemplate = messagingTemplate;
    }
    @MessageMapping("/driver/updateLocation")
    public void receiveDriverLocation(DriverLocationUpdateDTO dto) {
        driverLocationService.updateDriverLocation(dto);
    }
    /**
     * Rider sends their live GPS location.
     * Re-broadcast to /topic/ride/{rideId}/passenger-location
     * so the driver can see where the rider is on the map.
     */
    @MessageMapping("/rider/updateLocation")
    public void receivePassengerLocation(PassengerLocationUpdateDTO dto) {
        if (dto.getRideId() != null && dto.getPassengerId() != null) {
            messagingTemplate.convertAndSend(
                "/topic/ride/" + dto.getRideId() + "/passenger-location",
                new PassengerLocationBroadcastDTO(
                    dto.getPassengerId(),
                    dto.getLatitude(),
                    dto.getLongitude()
                )
            );
        }
    }
}
