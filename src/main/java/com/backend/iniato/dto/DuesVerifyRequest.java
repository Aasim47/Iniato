package com.backend.iniato.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * The three values Razorpay's checkout returns on success, which together
 * prove the payment happened.
 */
@Data
public class DuesVerifyRequest {

    @NotBlank
    private String razorpayOrderId;

    @NotBlank
    private String razorpayPaymentId;

    @NotBlank
    private String razorpaySignature;
}
