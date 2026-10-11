package com.backend.iniato.config;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Thrown when a driver tries to take on work while owing Iniato too much.
 *
 * <p>Deliberately <b>not</b> an {@link IllegalStateException}: that is mapped to
 * 409 by {@link GlobalExceptionHandler}, whereas this must surface as 402 so
 * the driver app can recognise it and route to the payment screen.
 */
@Getter
public class DuesExceededException extends RuntimeException {

    private final BigDecimal balance;
    private final BigDecimal threshold;

    public DuesExceededException(BigDecimal balance, BigDecimal threshold) {
        super(String.format(
                "Clear your pending dues of ₹%.2f to continue driving.", balance));
        this.balance = balance;
        this.threshold = threshold;
    }
}
