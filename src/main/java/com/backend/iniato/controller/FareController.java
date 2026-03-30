package com.backend.iniato.controller;

import com.backend.iniato.dto.FareEstimateRequestDTO;
import com.backend.iniato.dto.FareEstimateResponseDTO;
import com.backend.iniato.services.FareCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fare")
public class FareController {

    private final FareCalculationService fareCalculationService;


    @Autowired
    public FareController(FareCalculationService fareCalculationService) {
        this.fareCalculationService = fareCalculationService;
    }

    // 🧮 Estimate fare for shared ride (split among passengers)
    @PostMapping("/estimate")
    public ResponseEntity<FareEstimateResponseDTO> estimateSharedFare(@RequestBody FareEstimateRequestDTO request) {
        FareEstimateResponseDTO response = fareCalculationService.calculateSharedFare(request);
        return ResponseEntity.ok(response);
    }
}
