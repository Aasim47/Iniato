package com.backend.iniato.dto;

import java.math.BigDecimal;

/**
 * Platform revenue on a single non-pooled ride.
 *
 * <p>Every fee here is <em>carved out of</em> {@link #gross}, never added to it:
 * the rider pays exactly the fare they were quoted, and the driver keeps
 * {@link #driverNet} of the cash they collect.
 *
 * @param gross            fare quoted to and paid by the rider
 * @param convenienceFee   flat per-rider fee, by slab
 * @param riderCommission  percentage cut attributed to the rider side
 * @param driverCommission percentage cut attributed to the driver side
 * @param platformTotal    what Iniato earns: the three fees above
 * @param driverNet        {@code gross - platformTotal}
 */
public record FeeBreakdown(
        BigDecimal gross,
        BigDecimal convenienceFee,
        BigDecimal riderCommission,
        BigDecimal driverCommission,
        BigDecimal platformTotal,
        BigDecimal driverNet
) {}
