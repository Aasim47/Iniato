package com.backend.iniato.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/driver")
public class DriverController {
    @PostMapping("/activate")
    public void activate() {}

    @PostMapping("/set-online")
    public void setOnline() {}

    @PostMapping("/create-route")
    public void createRoute() {}

    @GetMapping("/routes/active")
    public void getActiveRoutes() {}

    @PostMapping("/end-route")
    public void endRoute() {}
}

