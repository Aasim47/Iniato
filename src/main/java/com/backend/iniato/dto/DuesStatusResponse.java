package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A driver's standing with Iniato: what they owe, and whether it stops them
 * working.
 */
@Data
@Builder
public class DuesStatusResponse {

    /** Currently owed. */
    private BigDecimal balance;

    /** Balance at which driving is blocked. */
    private BigDecimal threshold;

    /** {@code balance >= threshold} — the driver cannot take new work. */
    private boolean blocked;

    /** How much must be paid to get back under the threshold. */
    private BigDecimal amountToUnblock;

    private BigDecimal lifetimeAccrued;
    private BigDecimal lifetimePaid;
    private LocalDateTime lastPaidAt;
}
