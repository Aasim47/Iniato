package com.backend.iniato.controller;

import com.backend.iniato.dto.PaymentRequestDTO;
import com.backend.iniato.dto.PaymentResponseDTO;
import com.backend.iniato.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/split")
    public ResponseEntity<PaymentResponseDTO> splitFare(@RequestBody PaymentRequestDTO request) {
        PaymentResponseDTO response = paymentService.splitSharedFare(request);
        return ResponseEntity.ok(response);
    }
}
