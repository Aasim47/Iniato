package com.backend.iniato.services;

import com.backend.iniato.config.FeeProperties;
import com.backend.iniato.dto.FeeBreakdown;
import com.backend.iniato.dto.PooledSettlement;
import com.backend.iniato.dto.RiderLeg;
import com.backend.iniato.dto.RiderShare;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in Iniato's revenue model. These are the numbers quoted to the
 * business, so a rate change should fail here loudly rather than silently
 * repricing every ride — same contract as {@link FareCalculationServiceTest}
 * holds for the fare bands themselves.
 */
class PlatformFeeServiceTest {

    /** Service wired with the shipped default rates. */
    private static PlatformFeeService withDefaults() {
        return serviceWith(new FeeProperties());
    }

    private static PlatformFeeService serviceWith(FeeProperties props) {
        props.parseSlabs();
        return new PlatformFeeService(props);
    }

    private static BigDecimal rs(String amount) {
        return new BigDecimal(amount);
    }

    private static void assertRs(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected ₹" + expected + " but got ₹" + actual);
    }

    // ─── Pooled rides ────────────────────────────────────────────────────────

    @Test
    @DisplayName("₹80 pooled across 10/4/4/2 km splits 40/16/16/8 and earns ₹9.71")
    void pooledRideWorkedExample() {
        PooledSettlement s = withDefaults().forPooledRide(rs("80.00"), List.of(
                new RiderLeg(1L, 10.0),
                new RiderLeg(2L, 4.0),
                new RiderLeg(3L, 4.0),
                new RiderLeg(4L, 2.0)));

        assertRs("40.00", s.shares().get(0).share());
        assertRs("16.00", s.shares().get(1).share());
        assertRs("16.00", s.shares().get(2).share());
        assertRs("8.00", s.shares().get(3).share());

        // Slabs: ₹40 and ₹16 sit in the ≤60 and ≤30 bands respectively.
        assertRs("3.00", s.shares().get(0).convenienceFee());
        assertRs("2.00", s.shares().get(1).convenienceFee());
        assertRs("9.00", s.convenienceTotal());

        // 1% of (80 - 9)
        assertRs("0.71", s.driverCommission());
        assertRs("9.71", s.platformTotal());
        assertRs("70.29", s.driverNet());
    }

    @Test
    @DisplayName("A rider's share nets down by exactly their convenience fee")
    void netShareIsShareMinusFee() {
        PooledSettlement s = withDefaults().forPooledRide(rs("80.00"), List.of(
                new RiderLeg(1L, 10.0), new RiderLeg(2L, 2.0)));

        for (RiderShare share : s.shares()) {
            assertRs(share.share().subtract(share.convenienceFee()).toPlainString(),
                    share.netShare());
        }
    }

    @Test
    @DisplayName("Shares always sum to the ride total, despite rounding")
    void sharesSumToTotalExactly() {
        // 7.3/2.1/1.9/0.7 km over ₹80 rounds to ₹80.01 if each share is
        // rounded independently — the remainder correction must absorb it.
        assertSharesSumTo("80.00", List.of(
                new RiderLeg(1L, 7.3), new RiderLeg(2L, 2.1),
                new RiderLeg(3L, 1.9), new RiderLeg(4L, 0.7)));

        // Three-way splits are the classic 1/3 rounding trap.
        assertSharesSumTo("100.00", List.of(
                new RiderLeg(1L, 1.0), new RiderLeg(2L, 1.0), new RiderLeg(3L, 1.0)));

        assertSharesSumTo("57.35", List.of(
                new RiderLeg(1L, 3.33), new RiderLeg(2L, 3.33), new RiderLeg(3L, 3.34)));
    }

    private void assertSharesSumTo(String total, List<RiderLeg> legs) {
        PooledSettlement s = withDefaults().forPooledRide(rs(total), legs);
        BigDecimal sum = s.shares().stream()
                .map(RiderShare::share)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertRs(total, sum);
    }

    @Test
    @DisplayName("Riders with no distance recorded split the fare equally")
    void zeroDistanceFallsBackToEqualSplit() {
        PooledSettlement s = withDefaults().forPooledRide(rs("80.00"), List.of(
                new RiderLeg(1L, 0.0), new RiderLeg(2L, 0.0),
                new RiderLeg(3L, 0.0), new RiderLeg(4L, 0.0)));

        for (RiderShare share : s.shares()) {
            assertRs("20.00", share.share());
        }
    }

    @Test
    @DisplayName("A pooled ride with no riders earns nothing and charges nothing")
    void emptyPoolIsZero() {
        PooledSettlement s = withDefaults().forPooledRide(rs("80.00"), List.of());
        assertTrue(s.shares().isEmpty());
        assertRs("0.00", s.platformTotal());
    }

    @Test
    @DisplayName("A convenience fee never exceeds the share it comes out of")
    void feeIsCappedAtTheShare() {
        // A ₹1 pool split four ways leaves shares far below the ₹2 minimum fee.
        PooledSettlement s = withDefaults().forPooledRide(rs("1.00"), List.of(
                new RiderLeg(1L, 1.0), new RiderLeg(2L, 1.0),
                new RiderLeg(3L, 1.0), new RiderLeg(4L, 1.0)));

        for (RiderShare share : s.shares()) {
            assertTrue(share.convenienceFee().compareTo(share.share()) <= 0,
                    "fee ₹" + share.convenienceFee() + " exceeded share ₹" + share.share());
            assertTrue(share.netShare().signum() >= 0, "net share went negative");
        }
        assertTrue(s.driverNet().signum() >= 0, "driver net went negative");
    }

    // ─── Direct rides ────────────────────────────────────────────────────────

    @Test
    @DisplayName("₹80 direct ride earns ₹12 and leaves the driver ₹68")
    void directRideWorkedExample() {
        FeeBreakdown b = withDefaults().forDirectRide(rs("80.00"));

        assertRs("4.00", b.convenienceFee());   // 60 < 80 ≤ 100
        assertRs("4.00", b.riderCommission());  // 5%
        assertRs("4.00", b.driverCommission()); // 5%
        assertRs("12.00", b.platformTotal());
        assertRs("68.00", b.driverNet());
    }

    @Test
    @DisplayName("A zero or missing fare earns nothing rather than throwing")
    void directRideHandlesNoFare() {
        assertRs("0.00", withDefaults().forDirectRide(BigDecimal.ZERO).platformTotal());
        assertRs("0.00", withDefaults().forDirectRide(null).platformTotal());
    }

    // ─── Convenience-fee slabs ───────────────────────────────────────────────

    @Test
    @DisplayName("Slab bounds are inclusive at exactly 30, 60 and 100")
    void slabBoundariesAreInclusive() {
        PlatformFeeService service = withDefaults();

        assertRs("2.00", service.convenienceFeeFor(rs("29.99")));
        assertRs("2.00", service.convenienceFeeFor(rs("30.00")));
        assertRs("3.00", service.convenienceFeeFor(rs("30.01")));

        assertRs("3.00", service.convenienceFeeFor(rs("60.00")));
        assertRs("4.00", service.convenienceFeeFor(rs("60.01")));

        assertRs("4.00", service.convenienceFeeFor(rs("100.00")));
        assertRs("5.00", service.convenienceFeeFor(rs("100.01")));
        assertRs("5.00", service.convenienceFeeFor(rs("5000.00")));
    }

    // ─── Configurability ─────────────────────────────────────────────────────

    @Test
    @DisplayName("A flat ₹4 slab config reproduces the original ₹16.64 model")
    void flatFeeConfigReproducesOriginalBrief() {
        FeeProperties flat = new FeeProperties();
        flat.getConvenience().setSlabs("*:4");

        PooledSettlement s = serviceWith(flat).forPooledRide(rs("80.00"), List.of(
                new RiderLeg(1L, 10.0), new RiderLeg(2L, 4.0),
                new RiderLeg(3L, 4.0), new RiderLeg(4L, 2.0)));

        assertRs("16.00", s.convenienceTotal());
        assertRs("0.64", s.driverCommission());  // 1% of (80 - 16)
        assertRs("16.64", s.platformTotal());
        assertRs("63.36", s.driverNet());
    }

    @Test
    @DisplayName("Commission rates come from config, not from the code")
    void commissionRatesAreConfigurable() {
        FeeProperties props = new FeeProperties();
        props.getDirect().setRiderCommission(new BigDecimal("0.10"));
        props.getDirect().setDriverCommission(new BigDecimal("0.02"));

        FeeBreakdown b = serviceWith(props).forDirectRide(rs("80.00"));
        assertRs("8.00", b.riderCommission());
        assertRs("1.60", b.driverCommission());
    }

    @Test
    @DisplayName("A slab table with no catch-all fails at startup, not at fare time")
    void malformedSlabsFailFast() {
        FeeProperties noCatchAll = new FeeProperties();
        noCatchAll.getConvenience().setSlabs("30:2,60:3");
        assertThrows(IllegalStateException.class, noCatchAll::parseSlabs);

        FeeProperties malformed = new FeeProperties();
        malformed.getConvenience().setSlabs("30,*:5");
        assertThrows(IllegalStateException.class, malformed::parseSlabs);
    }

    @Test
    @DisplayName("Slabs are applied in ascending order regardless of config order")
    void slabsAreSortedRegardlessOfConfigOrder() {
        FeeProperties shuffled = new FeeProperties();
        shuffled.getConvenience().setSlabs("100:4,*:5,30:2,60:3");

        PlatformFeeService service = serviceWith(shuffled);
        assertRs("2.00", service.convenienceFeeFor(rs("25.00")));
        assertRs("3.00", service.convenienceFeeFor(rs("45.00")));
        assertRs("4.00", service.convenienceFeeFor(rs("90.00")));
        assertRs("5.00", service.convenienceFeeFor(rs("150.00")));
    }
}
