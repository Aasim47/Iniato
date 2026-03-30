package com.backend.iniato.services;

import com.backend.iniato.dto.*;
import com.backend.iniato.entity.Route;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcast a new active driver route to all connected riders.
     */
    public void broadcastNewRoute(RouteResponseDTO routeDTO) {
        messagingTemplate.convertAndSend("/topic/routes/new", routeDTO);
    }

    /**
     * Notify a specific driver of a new rider's join request.
     * Driver listens on /topic/driver/{driverId}/requests
     */
    public void notifyDriverNewRequest(Long driverId, NewRiderRequestEvent event) {
        messagingTemplate.convertAndSend("/topic/driver/" + driverId + "/requests", event);
    }

    /**
     * Notify all passengers on a ride that a new passenger was accepted.
     * Passengers listen on /topic/ride/{rideId}
     */
    public void notifyPassengerAccepted(Long rideId, PassengerAddedEvent event) {
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }

    /**
     * Broadcast ride started event to all passengers.
     */
    public void broadcastRideStarted(Long rideId) {
        RideStartedEvent event = new RideStartedEvent();
        event.rideId = String.valueOf(rideId);
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }

    /**
     * Broadcast driver location update to all passengers on the ride.
     */
    public void broadcastLocationUpdate(Long rideId, DriverLocationUpdateEvent event) {
        messagingTemplate.convertAndSend("/topic/ride/" + rideId + "/location", event);
    }

    /**
     * Broadcast ride completed event to all passengers.
     */
    public void broadcastRideCompleted(Long rideId) {
        RideCompletedEvent event = new RideCompletedEvent();
        event.rideId = String.valueOf(rideId);
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }
}
