package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of a driver's dues ledger — an accrual or a payment.
 *
 * <p>Exists so the ledger is mapped inside {@code DriverDuesService}'s
 * transaction. The controller used to receive {@code DriverDuesTransaction}
 * entities and flatten them itself, which dereferenced the lazy
 * {@code settlement} association after the session had closed. That only
 * worked because open-in-view was on; with it off, every ACCRUAL row
 * (the ones that carry a settlement) would have thrown.
 */
@Data
@Builder
public class DuesLedgerEntry {

    private Long id;

    /** ACCRUAL or PAYMENT. */
    private String type;

    /** Positive for an accrual, negative for a payment. */
    private BigDecimal amount;

    private BigDecimal balanceAfter;
    private String note;
    private LocalDateTime createdAt;

    /** Ride this entry settles, when it is an accrual. Null for payments. */
    private Long rideId;

    /** POOLED or DIRECT — which table {@link #rideId} points into. */
    private String rideKind;
}
