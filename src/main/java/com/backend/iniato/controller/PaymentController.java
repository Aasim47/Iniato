package com.backend.iniato.controller;

import com.backend.iniato.dto.PaymentRequestDTO;
import com.backend.iniato.dto.PaymentResponseDTO;
import com.backend.iniato.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/split")
    public ResponseEntity<PaymentResponseDTO> splitFare(@RequestBody PaymentRequestDTO request) {
        PaymentResponseDTO response = paymentService.splitSharedFare(request);
        return ResponseEntity.ok(response);
    }
}
