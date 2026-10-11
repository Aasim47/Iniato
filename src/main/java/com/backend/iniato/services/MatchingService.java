package com.backend.iniato.services;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.dto.RouteResponseDTO;
import com.backend.iniato.entity.Route;

import java.util.List;
import java.util.Optional;

public interface MatchingService {

    Optional<Route> matchPassengerToRoute(RiderCreateRequest request);

    List<Route> fetchActiveRoutesNearby(double lat, double lng, double radiusKm);

    /**
     * Like {@link #matchPassengerToRoute}, but returns a detached DTO.
     *
     * <p>Exists so the controller never serializes a {@link Route} entity.
     * Doing that reached the lazy {@code driver} association during response
     * writing, which required {@code spring.jpa.open-in-view=true} — the
     * setting that turns one query into several per response — and exposed the
     * full {@code User}, password hash included. The mapping happens inside
     * this service's transaction instead.
     */
    Optional<RouteResponseDTO> matchPassengerToRouteDto(RiderCreateRequest request);

    /** DTO form of {@link #fetchActiveRoutesNearby}, mapped inside the transaction. */
    List<RouteResponseDTO> fetchActiveRouteDtosNearby(double lat, double lng, double radiusKm);
}
