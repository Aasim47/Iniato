package com.backend.iniato.services;

import com.backend.iniato.dto.FeeBreakdown;
import com.backend.iniato.dto.PooledSettlement;
import com.backend.iniato.dto.RiderLeg;
import com.backend.iniato.dto.RiderShare;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RideKind;
import com.backend.iniato.repo.RidePassengerRepository;
import com.backend.iniato.repo.RideSettlementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a finished ride into money: works out Iniato's cut and books it against
 * the driver.
 *
 * <p><b>This is the only place fees are charged.</b> A pooled ride can be
 * completed through two different endpoints — {@code /api/rides/{id}/complete}
 * and {@code /api/routes/{id}/complete} — and both are live. Centralising here,
 * behind the unique key on {@link RideSettlement}, is what stops a driver being
 * charged twice for one ride.
 *
 * <p>Settlement is deliberately <b>additive</b>: it reads the fares the existing
 * completion loops already compute and redistributes that same total by
 * distance. No rider pays more in aggregate than they do today.
 */
@Service
public class RideSettlementService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final RideSettlementRepository settlementRepository;
    private final RidePassengerRepository ridePassengerRepository;
    private final PlatformFeeService platformFeeService;
    private final DriverDuesService driverDuesService;

    public RideSettlementService(RideSettlementRepository settlementRepository,
                                 RidePassengerRepository ridePassengerRepository,
                                 PlatformFeeService platformFeeService,
                                 DriverDuesService driverDuesService) {
        this.settlementRepository = settlementRepository;
        this.ridePassengerRepository = ridePassengerRepository;
        this.platformFeeService = platformFeeService;
        this.driverDuesService = driverDuesService;
    }

    /**
     * Settle a completed pooled ride.
     *
     * <p>The ride total is the sum of the fares the caller already computed and
     * stored on each {@link RidePassenger#getFareShare()}; that total is then
     * re-split by distance travelled, so a rider going twice as far pays twice
     * as much. Each rider's {@code fareShare} and {@code convenienceFee} are
     * rewritten with their settled values.
     *
     * @param ride       the completed ride
     * @param passengers everyone who actually travelled — {@code COMPLETED} and
     *                   mid-ride {@code DROPPED} alike
     * @return the settlement, or empty if this ride was already settled
     */
    @Transactional
    public Optional<RideSettlement> settlePooledRide(Ride ride, List<RidePassenger> passengers) {
        if (ride == null || ride.getId() == null) return Optional.empty();
        if (settlementRepository.existsByRideKindAndRideId(RideKind.POOLED, ride.getId())) {
            return Optional.empty();
        }

        List<RidePassenger> paying = passengers == null ? List.of() : passengers.stream()
                .filter(rp -> rp.getPassenger() != null)
                .toList();
        if (paying.isEmpty()) return Optional.empty();

        BigDecimal total = paying.stream()
                .map(rp -> money(rp.getFareShare() == null
                        ? BigDecimal.ZERO : BigDecimal.valueOf(rp.getFareShare())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) return Optional.empty();

        List<RiderLeg> legs = paying.stream()
                .map(rp -> new RiderLeg(rp.getPassenger().getId(), straightLineKmOf(rp)))
                .toList();

        PooledSettlement settled = platformFeeService.forPooledRide(total, legs);

        // Write each rider's settled share back, so the receipt they see and
        // the amount the driver collects agree with what was charged.
        List<RiderShare> shares = settled.shares();
        for (int i = 0; i < paying.size() && i < shares.size(); i++) {
            RidePassenger rp = paying.get(i);
            RiderShare share = shares.get(i);
            rp.setFareShare(share.share().doubleValue());
            rp.setConvenienceFee(share.convenienceFee());
        }
        ridePassengerRepository.saveAll(paying);

        RideSettlement settlement = RideSettlement.builder()
                .rideKind(RideKind.POOLED)
                .rideId(ride.getId())
                .driver(ride.getDriver())
                .grossFare(settled.total())
                .convenienceFee(settled.convenienceTotal())
                .riderCommission(money(BigDecimal.ZERO))
                .driverCommission(settled.driverCommission())
                .platformTotal(settled.platformTotal())
                .driverNet(settled.driverNet())
                .passengerCount(paying.size())
                .build();

        return persistAndAccrue(settlement, RideKind.POOLED, ride.getId(), ride.getDriver());
    }

    /**
     * Settle a completed direct ride: a convenience fee plus a percentage from
     * each side.
     *
     * @return the settlement, or empty if this ride was already settled
     */
    @Transactional
    public Optional<RideSettlement> settleDirectRide(DirectRide ride) {
        if (ride == null || ride.getId() == null) return Optional.empty();
        if (settlementRepository.existsByRideKindAndRideId(RideKind.DIRECT, ride.getId())) {
            return Optional.empty();
        }

        BigDecimal fare = money(ride.getEstimatedFare() == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(ride.getEstimatedFare()));
        if (fare.signum() <= 0) return Optional.empty();

        FeeBreakdown fees = platformFeeService.forDirectRide(fare);

        RideSettlement settlement = RideSettlement.builder()
                .rideKind(RideKind.DIRECT)
                .rideId(ride.getId())
                .driver(ride.getDriver())
                .grossFare(fees.gross())
                .convenienceFee(fees.convenienceFee())
                .riderCommission(fees.riderCommission())
                .driverCommission(fees.driverCommission())
                .platformTotal(fees.platformTotal())
                .driverNet(fees.driverNet())
                .passengerCount(ride.getPassengerCount() == null ? 1 : ride.getPassengerCount())
                .build();

        return persistAndAccrue(settlement, RideKind.DIRECT, ride.getId(), ride.getDriver());
    }

    /** Look up an existing settlement, for receipts and earnings views. */
    @Transactional(readOnly = true)
    public Optional<RideSettlement> findSettlement(RideKind kind, Long rideId) {
        return settlementRepository.findByRideKindAndRideId(kind, rideId);
    }

    /**
     * Save the settlement and bill the driver for it.
     * <p>
     * Two rides completing at once, or one ride completed through both
     * endpoints, collide on the unique {@code (ride_kind, ride_id)} key — which
     * is treated as "already settled" rather than an error.
     */
    private Optional<RideSettlement> persistAndAccrue(RideSettlement settlement,
                                                      RideKind kind, Long rideId, User driver) {
        RideSettlement saved;
        try {
            saved = settlementRepository.save(settlement);
        } catch (DataIntegrityViolationException alreadySettled) {
            return Optional.empty();
        }

        if (driver != null && saved.getPlatformTotal().signum() > 0) {
            driverDuesService.accrue(
                    driver, saved.getPlatformTotal(), dedupeKey(kind, rideId), saved);
        }
        return Optional.of(saved);
    }

    /** Stable per-ride key so a replayed completion cannot accrue twice. */
    private static String dedupeKey(RideKind kind, Long rideId) {
        return kind.name() + ":" + rideId + ":ACCRUAL";
    }

    /**
     * How far this passenger travelled, as the crow flies.
     * <p>
     * Only the <em>ratio</em> between riders matters to the split, so the
     * road-distance factor cancels out and is not applied here.
     */
    private static double straightLineKmOf(RidePassenger rp) {
        if (rp.getPickupLocation() == null || rp.getDestinationLocation() == null) return 0.0;
        return haversineKm(
                rp.getPickupLocation().getY(), rp.getPickupLocation().getX(),
                rp.getDestinationLocation().getY(), rp.getDestinationLocation().getX());
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
