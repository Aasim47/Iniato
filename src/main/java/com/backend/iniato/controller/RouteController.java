package com.backend.iniato.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/routes")
public class RouteController {
    @PostMapping("")
    public void createRoute() {}

    @GetMapping("/{id}")
    public void getRoute(@PathVariable String id) {}

    @PatchMapping("/{id}/update-location")
    public void updateLocation(@PathVariable String id) {}

    @PatchMapping("/{id}/add-stop")
    public void addStop(@PathVariable String id) {}

    @PatchMapping("/{id}/complete")
    public void completeRoute(@PathVariable String id) {}
}

