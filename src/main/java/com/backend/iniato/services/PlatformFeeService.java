package com.backend.iniato.services;

import com.backend.iniato.config.FeeProperties;
import com.backend.iniato.dto.FeeBreakdown;
import com.backend.iniato.dto.PooledSettlement;
import com.backend.iniato.dto.RiderLeg;
import com.backend.iniato.dto.RiderShare;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out what Iniato earns on a ride.
 * <p>
 * Deliberately separate from {@link FareCalculationService}, which answers
 * "what does this trip cost?". This service answers "of that, what is ours?" —
 * a policy question whose rates live in {@link FeeProperties}.
 *
 * <p><b>Fees are carved out, never added on.</b> A rider quoted ₹20 pays ₹20;
 * the convenience fee comes out of that ₹20, it is not a ₹24 checkout. Riders
 * pay the driver in cash, so the platform's share becomes a debt the driver
 * owes — see {@code DriverDuesService}.
 *
 * <p><b>Money is {@link BigDecimal} at scale 2 throughout.</b> Doubles are used
 * only for distances, which are already approximations.
 */
@Service
public class PlatformFeeService {

    /** Every rupee figure Iniato quotes, charges or banks is 2dp. */
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = money(BigDecimal.ZERO);

    private final FeeProperties feeProperties;

    public PlatformFeeService(FeeProperties feeProperties) {
        this.feeProperties = feeProperties;
    }

    /**
     * Revenue on a direct (non-pooled) ride: a convenience fee plus a
     * percentage from each side.
     *
     * <pre>
     *   ₹80 fare → ₹4 convenience + ₹4 rider 5% + ₹4 driver 5%
     *            → ₹12 to Iniato, ₹68 kept by the driver
     * </pre>
     */
    public FeeBreakdown forDirectRide(BigDecimal fare) {
        BigDecimal gross = money(fare == null ? BigDecimal.ZERO : fare);
        if (gross.signum() <= 0) {
            return new FeeBreakdown(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
        }

        BigDecimal convenienceFee = convenienceFeeFor(gross);
        BigDecimal riderCommission = pct(gross, feeProperties.getDirect().getRiderCommission());
        BigDecimal driverCommission = pct(gross, feeProperties.getDirect().getDriverCommission());

        BigDecimal platformTotal = convenienceFee.add(riderCommission).add(driverCommission);
        // A pathological rate config must never leave the driver owing more
        // than they collected.
        platformTotal = platformTotal.min(gross);

        return new FeeBreakdown(
                gross, convenienceFee, riderCommission, driverCommission,
                platformTotal, gross.subtract(platformTotal));
    }

    /**
     * Revenue on a pooled ride.
     * <p>
     * The total is split across riders in proportion to the distance each
     * actually travels, a convenience fee is carved out of every share, and the
     * driver pays a percentage of what remains.
     *
     * <pre>
     *   ₹80 over 10/4/4/2 km → shares ₹40/₹16/₹16/₹8
     *                        → fees   ₹3 /₹2 /₹2 /₹2  = ₹9
     *                        → driver 1% of (80-9)     = ₹0.71
     *                        → ₹9.71 to Iniato, ₹70.29 kept by the driver
     * </pre>
     *
     * @param total the whole ride's fare
     * @param legs  one entry per rider; an empty list yields a zero settlement
     */
    public PooledSettlement forPooledRide(BigDecimal total, List<RiderLeg> legs) {
        BigDecimal gross = money(total == null ? BigDecimal.ZERO : total);
        if (legs == null || legs.isEmpty() || gross.signum() <= 0) {
            return new PooledSettlement(gross.max(ZERO), List.of(), ZERO, ZERO, ZERO, gross.max(ZERO));
        }

        List<BigDecimal> amounts = splitByDistance(gross, legs);

        List<RiderShare> shares = new ArrayList<>(legs.size());
        BigDecimal convenienceTotal = ZERO;
        for (int i = 0; i < legs.size(); i++) {
            RiderLeg leg = legs.get(i);
            BigDecimal share = amounts.get(i);
            // Cap the fee at the share itself: a ₹1.50 hop must not owe ₹2.
            BigDecimal fee = convenienceFeeFor(share).min(share);
            convenienceTotal = convenienceTotal.add(fee);
            shares.add(new RiderShare(
                    leg.passengerId(), leg.roadKm(), share, fee, share.subtract(fee)));
        }

        BigDecimal netBase = gross.subtract(convenienceTotal);
        BigDecimal driverCommission = pct(netBase, feeProperties.getPool().getDriverCommission());

        BigDecimal platformTotal = convenienceTotal.add(driverCommission).min(gross);

        return new PooledSettlement(
                gross, List.copyOf(shares), convenienceTotal, driverCommission,
                platformTotal, gross.subtract(platformTotal));
    }

    /**
     * The convenience fee for a single rider's share, by slab.
     * Bounds are inclusive: a share of exactly ₹30 falls in the {@code 30:} band.
     */
    public BigDecimal convenienceFeeFor(BigDecimal share) {
        BigDecimal amount = money(share == null ? BigDecimal.ZERO : share);
        if (amount.signum() <= 0) return ZERO;

        for (FeeProperties.Slab slab : feeProperties.getParsedSlabs()) {
            if (slab.covers(amount)) return money(slab.fee());
        }
        return ZERO; // unreachable: the slab list always ends in a catch-all
    }

    /**
     * Split {@code total} across riders by distance travelled, such that the
     * parts sum to {@code total} <em>exactly</em>.
     *
     * <p>Rounding each weighted share independently drifts — ₹80 over
     * 7.3/2.1/1.9/0.7 km rounds to ₹80.01. So the remainder is handed to the
     * largest share, which absorbs a paise without looking wrong.
     *
     * <p>Riders with no usable distance (a zero-length leg, or a total distance
     * of zero) fall back to an equal split rather than riding free.
     */
    private List<BigDecimal> splitByDistance(BigDecimal total, List<RiderLeg> legs) {
        double totalKm = legs.stream()
                .mapToDouble(leg -> Math.max(0.0, leg.roadKm()))
                .sum();

        List<BigDecimal> amounts = new ArrayList<>(legs.size());

        if (totalKm <= 0.0) {
            // No distance information — everyone pays the same.
            BigDecimal even = total.divide(
                    BigDecimal.valueOf(legs.size()), MONEY_SCALE, MONEY_ROUNDING);
            for (int i = 0; i < legs.size(); i++) amounts.add(even);
        } else {
            for (RiderLeg leg : legs) {
                double weight = Math.max(0.0, leg.roadKm()) / totalKm;
                amounts.add(money(total.multiply(BigDecimal.valueOf(weight))));
            }
        }

        applyRemainderToLargest(total, amounts);
        return amounts;
    }

    /** Push the rounding drift onto the biggest share so the parts sum exactly. */
    private void applyRemainderToLargest(BigDecimal total, List<BigDecimal> amounts) {
        BigDecimal sum = amounts.stream().reduce(ZERO, BigDecimal::add);
        BigDecimal drift = total.subtract(sum);
        if (drift.signum() == 0) return;

        int largest = 0;
        for (int i = 1; i < amounts.size(); i++) {
            if (amounts.get(i).compareTo(amounts.get(largest)) > 0) largest = i;
        }
        amounts.set(largest, amounts.get(largest).add(drift));
    }

    /** {@code amount × rate}, rounded to paise. */
    private BigDecimal pct(BigDecimal amount, BigDecimal rate) {
        if (amount.signum() <= 0 || rate == null || rate.signum() <= 0) return ZERO;
        return money(amount.multiply(rate));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
