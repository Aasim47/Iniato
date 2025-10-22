package com.backend.iniato.dto;

import lombok.Data;

@Data
public class PaymentRequestDTO {
    private Long rideId;
    private Long passengerId;
    private double amount;
    private String paymentMethod;
}