package com.backend.iniato.repo;

import com.backend.iniato.entity.Route;
import com.backend.iniato.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    List<RouteStop> findByRouteOrderBySequenceOrder(Route route);
}
