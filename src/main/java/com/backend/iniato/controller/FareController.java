package com.backend.iniato.controller;

import com.backend.iniato.dto.FareEstimateRequestDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import com.backend.iniato.services.FareCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fare")
@RequiredArgsConstructor
public class FareController {

    private final FareCalculationService fareCalculationService;

    @PostMapping("/estimate")
    public ResponseEntity<FareEstimateResponseDTO> estimateFare(@RequestBody FareEstimateRequestDTO request) {
        FareEstimateResponseDTO response = fareCalculationService.calculateFare(request);
        return ResponseEntity.ok(response);
    }
}
