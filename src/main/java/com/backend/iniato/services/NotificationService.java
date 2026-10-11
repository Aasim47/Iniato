package com.backend.iniato.services;
import com.backend.iniato.dto.*;
import com.backend.iniato.entity.DirectRide;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    /** Broadcast a new active driver route to all connected riders. */
    public void broadcastNewRoute(RouteResponseDTO routeDTO) {
        messagingTemplate.convertAndSend("/topic/routes/new", routeDTO);
    }
    /** Notify a specific driver of a new rider's join request. */
    public void notifyDriverNewRequest(Long driverId, NewRiderRequestEvent event) {
        messagingTemplate.convertAndSend("/topic/driver/" + driverId + "/requests", event);
    }
    /** Notify all passengers on a ride that a new passenger was accepted. */
    public void notifyPassengerAccepted(Long rideId, PassengerAddedEvent event) {
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }
    /** Broadcast ride started event to all passengers. */
    public void broadcastRideStarted(Long rideId) {
        RideStartedEvent event = new RideStartedEvent();
        event.rideId = String.valueOf(rideId);
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }
    /** Notify all passengers on a ride that a specific passenger was dropped off. */
    public void notifyPassengerDropped(Long rideId, DropPassengerEvent event) {
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }
    /** Notify the driver's ride view that a passenger left the pool (not a drop-off). */
    public void notifyPassengerLeft(Long rideId, PassengerLeftEvent event) {
        messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
    }
    /**
     * Broadcast driver location update to all passengers on the ride.
     *
     * <p>Takes {@link DriverLocationBroadcastDTO} — {@code driverId} as a number
     * plus {@code latitude}/{@code longitude} — because that is what the rider
     * app parses. It previously took {@code DriverLocationUpdateEvent}, whose
     * {@code lat}/{@code lng} (and String id) matched no client field, so every
     * value arrived as 0.
     */
    public void broadcastLocationUpdate(Long rideId, DriverLocationBroadcastDTO event) {
        messagingTemplate.convertAndSend("/topic/ride/" + rideId + "/location", event);
    }
    /**
     * Broadcast ride-completed event, once per remaining passenger with their fare.
     * Riders who were already individually dropped will ignore this via _alreadyDropped flag.
     */
    public void broadcastRideCompleted(Long rideId, List<RideCompletedEvent> events) {
        for (RideCompletedEvent event : events) {
            messagingTemplate.convertAndSend("/topic/ride/" + rideId, event);
        }
    }
    /** Notify driver that the ride was auto-cancelled (last rider left). */
    public void notifyDriverRideCancelled(Long driverId, RideCancelledEvent event) {
        messagingTemplate.convertAndSend("/topic/driver/" + driverId + "/ride-cancelled", event);
    }
    /** Broadcast updated route info (seat count, status) to all waiting riders. */
    public void broadcastRouteUpdated(RouteResponseDTO routeDTO) {
        messagingTemplate.convertAndSend("/topic/routes/updated", routeDTO);
    }

    /** Broadcast a new direct ride request to all available drivers. */
    public void broadcastDirectRideRequest(DirectRideResponseDTO directRide) {
        messagingTemplate.convertAndSend("/topic/direct-rides/new", directRide);
    }

    /** Notify driver that a direct ride was accepted. */
    public void notifyDriverDirectRideAccepted(DirectRideResponseDTO directRide) {
        messagingTemplate.convertAndSend("/topic/driver/" + directRide.getDriverId() + "/direct-ride-accepted", directRide);
    }

    /** Notify driver that a direct ride was declined. */
    public void notifyDriverDirectRideDeclined(DirectRideResponseDTO directRide) {
        messagingTemplate.convertAndSend("/topic/driver/" + directRide.getDriverId() + "/direct-ride-declined", directRide);
    }

    /** Broadcast that a direct ride has started. */
    public void broadcastDirectRideStarted(DirectRideResponseDTO directRide) {
        messagingTemplate.convertAndSend("/topic/direct-rides/" + directRide.getId() + "/started", directRide);
    }

    /** Broadcast that a direct ride has completed. */
    public void broadcastDirectRideCompleted(DirectRideResponseDTO directRide) {
        messagingTemplate.convertAndSend("/topic/direct-rides/" + directRide.getId() + "/completed", directRide);
    }

    /** Broadcast that a direct ride was cancelled. */
    public void broadcastDirectRideCancelled(DirectRideResponseDTO directRide) {
        messagingTemplate.convertAndSend("/topic/direct-rides/" + directRide.getId() + "/cancelled", directRide);
    }
}
