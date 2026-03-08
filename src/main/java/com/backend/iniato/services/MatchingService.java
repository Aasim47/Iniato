package com.backend.iniato.services;

import com.backend.iniato.dto.RiderCreateRequest;
import com.backend.iniato.entity.Route;
import java.util.List;
import java.util.Optional;

public interface MatchingService {
    Optional<Route> matchPassengerToRoute(RiderCreateRequest request);
    List<Route> fetchActiveRoutesNearby(double lat, double lng, double radiusKm);
}
