package com.backend.iniato.services;

import com.backend.iniato.dto.PaymentRequestDTO;
import com.backend.iniato.dto.PaymentResponseDTO;
import com.backend.iniato.entity.Payment;
import com.backend.iniato.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentResponseDTO processPayment(PaymentRequestDTO request) {
        // mock payment logic for now
        String status = "SUCCESS";
        String message = "Payment successful";

        Payment payment = Payment.builder()
                .ride(com.backend.iniato.entity.Ride.builder().id(request.getRideId()).build())
                .passenger(com.backend.iniato.entity.User.builder().id(request.getPassengerId()).build())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        return new PaymentResponseDTO(status, message, request.getAmount());
    }
}
