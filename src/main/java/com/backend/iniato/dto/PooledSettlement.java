package com.backend.iniato.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Platform revenue on a completed pooled ride.
 *
 * <p>{@code Σ shares == total} always holds exactly — see
 * {@code PlatformFeeService.splitByDistance}.
 *
 * @param total            the ride's whole fare, split across {@link #shares}
 * @param shares           per-rider breakdown, in the order supplied
 * @param convenienceTotal sum of every rider's convenience fee
 * @param driverCommission percentage cut on the fare net of convenience fees
 * @param platformTotal    what Iniato earns
 * @param driverNet        {@code total - platformTotal}
 */
public record PooledSettlement(
        BigDecimal total,
        List<RiderShare> shares,
        BigDecimal convenienceTotal,
        BigDecimal driverCommission,
        BigDecimal platformTotal,
        BigDecimal driverNet
) {}
