package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * A Razorpay order the driver app hands to the checkout sheet.
 */
@Data
@Builder
public class DuesOrderResponse {

    private String orderId;

    /** Amount in paise — what the Razorpay SDK expects. */
    private long amountPaise;

    /** The same amount in rupees, for display. */
    private BigDecimal amount;

    private String currency;

    /** Publishable key id. Safe to send to the client; the secret is not. */
    private String razorpayKeyId;
}
