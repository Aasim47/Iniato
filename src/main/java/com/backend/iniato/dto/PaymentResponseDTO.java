package com.backend.iniato.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResponseDTO {
    private String status;
    private String message;
    private double amount;
}
