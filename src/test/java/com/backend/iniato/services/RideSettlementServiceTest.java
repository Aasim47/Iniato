package com.backend.iniato.services;

import com.backend.iniato.config.FeeProperties;
import com.backend.iniato.entity.*;
import com.backend.iniato.enums.RideKind;
import com.backend.iniato.enums.RidePassengerStatus;
import com.backend.iniato.repo.RidePassengerRepository;
import com.backend.iniato.repo.RideSettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the one thing that would cost real money if it broke: a ride must be
 * billed exactly once.
 *
 * <p>This matters because a pooled ride has <b>two</b> live completion
 * endpoints — {@code /api/rides/{id}/complete} and
 * {@code /api/routes/{id}/complete} — and the driver app calls the latter.
 * Either can run first, and both can run.
 */
class RideSettlementServiceTest {

    private static final int SRID = 4326;
    private static final GeometryFactory GEO =
            new GeometryFactory(new PrecisionModel(), SRID);

    /** Sambalpur-ish origin; legs are laid out north of it. */
    private static final double BASE_LAT = 21.4669;
    private static final double BASE_LNG = 83.9812;

    private RideSettlementRepository settlements;
    private RidePassengerRepository ridePassengers;
    private RideSettlementService service;
    private RecordingDuesService dues;

    private final List<RideSettlement> savedSettlements = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedSettlements.clear();

        settlements = mock(RideSettlementRepository.class);
        ridePassengers = mock(RidePassengerRepository.class);

        when(settlements.existsByRideKindAndRideId(any(), any())).thenAnswer(i ->
                savedSettlements.stream().anyMatch(s ->
                        s.getRideKind() == i.getArgument(0)
                                && s.getRideId().equals(i.getArgument(1))));

        when(settlements.save(any())).thenAnswer((Answer<RideSettlement>) i -> {
            RideSettlement s = i.getArgument(0);
            // Stand in for the unique (ride_kind, ride_id) constraint.
            boolean clash = savedSettlements.stream().anyMatch(e ->
                    e.getRideKind() == s.getRideKind() && e.getRideId().equals(s.getRideId()));
            if (clash) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate settlement for " + s.getRideKind() + ":" + s.getRideId());
            }
            s.setId((long) (savedSettlements.size() + 1));
            savedSettlements.add(s);
            return s;
        });
        when(ridePassengers.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        FeeProperties props = new FeeProperties();
        props.parseSlabs();

        dues = new RecordingDuesService();
        service = new RideSettlementService(
                settlements, ridePassengers, new PlatformFeeService(props), dues);
    }

    private static void assertRs(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected ₹" + expected + " but got ₹" + actual);
    }

    /** A passenger travelling {@code km} north, already priced at {@code fare}. */
    private static RidePassenger passenger(long id, double km, double fare) {
        // ~111 km per degree of latitude.
        Point pickup = GEO.createPoint(new Coordinate(BASE_LNG, BASE_LAT));
        pickup.setSRID(SRID);
        Point drop = GEO.createPoint(new Coordinate(BASE_LNG, BASE_LAT + km / 111.0));
        drop.setSRID(SRID);

        return RidePassenger.builder()
                .id(id)
                .passenger(User.builder().id(id).phoneNumber("90000000" + id).build())
                .pickupLocation(pickup)
                .destinationLocation(drop)
                .status(RidePassengerStatus.COMPLETED)
                .fareShare(fare)
                .build();
    }

    private static Ride pooledRide() {
        return Ride.builder()
                .id(412L)
                .driver(User.builder().id(99L).phoneNumber("9111111111").build())
                .build();
    }

    @Test
    @DisplayName("A pooled ride is settled, billed, and the shares re-split by distance")
    void pooledRideSettlesAndBills() {
        Ride ride = pooledRide();
        // Four riders priced solo at ₹20 each = ₹80 total, travelling 10/4/4/2 km.
        List<RidePassenger> travelled = List.of(
                passenger(1L, 10.0, 20.0),
                passenger(2L, 4.0, 20.0),
                passenger(3L, 4.0, 20.0),
                passenger(4L, 2.0, 20.0));

        Optional<RideSettlement> result = service.settlePooledRide(ride, travelled);

        assertTrue(result.isPresent());
        RideSettlement s = result.get();
        assertRs("80.00", s.getGrossFare());
        assertRs("9.00", s.getConvenienceFee());
        assertRs("0.71", s.getDriverCommission());
        assertRs("9.71", s.getPlatformTotal());
        assertRs("70.29", s.getDriverNet());
        assertEquals(4, s.getPassengerCount());

        // The ₹80 is redistributed by distance, not left at ₹20 each.
        assertEquals(40.0, travelled.get(0).getFareShare(), 0.01);
        assertEquals(16.0, travelled.get(1).getFareShare(), 0.01);
        assertEquals(8.0, travelled.get(3).getFareShare(), 0.01);
        assertRs("3.00", travelled.get(0).getConvenienceFee());

        // And the driver is billed exactly the platform's cut.
        assertEquals(1, dues.accruals.size());
        assertRs("9.71", dues.accruals.get(0).amount());
    }

    @Test
    @DisplayName("Riders still pay, in total, exactly the ride's fare")
    void sharesStillSumToTheRideTotal() {
        List<RidePassenger> travelled = List.of(
                passenger(1L, 7.3, 20.0), passenger(2L, 2.1, 20.0),
                passenger(3L, 1.9, 20.0), passenger(4L, 0.7, 20.0));

        service.settlePooledRide(pooledRide(), travelled);

        double sum = travelled.stream().mapToDouble(RidePassenger::getFareShare).sum();
        assertEquals(80.0, sum, 0.001, "re-split must not create or destroy money");
    }

    @Test
    @DisplayName("Completing the same pooled ride twice bills the driver once")
    void pooledSettlementIsIdempotent() {
        Ride ride = pooledRide();
        List<RidePassenger> travelled = List.of(
                passenger(1L, 10.0, 20.0), passenger(2L, 4.0, 20.0),
                passenger(3L, 4.0, 20.0), passenger(4L, 2.0, 20.0));

        // First completion — say, via /api/routes/{id}/complete from the driver app.
        assertTrue(service.settlePooledRide(ride, travelled).isPresent());
        // Second — via /api/rides/{id}/complete. Must be a no-op.
        assertTrue(service.settlePooledRide(ride, travelled).isEmpty());

        assertEquals(1, savedSettlements.size(), "only one settlement row may exist");
        assertEquals(1, dues.accruals.size(), "the driver must be billed only once");
        assertRs("9.71", dues.accruals.get(0).amount());
    }

    @Test
    @DisplayName("A direct ride is settled at convenience + 5% + 5%")
    void directRideSettles() {
        DirectRide ride = DirectRide.builder()
                .id(7L)
                .driver(User.builder().id(99L).build())
                .estimatedFare(80.0)
                .passengerCount(1)
                .build();

        RideSettlement s = service.settleDirectRide(ride).orElseThrow();

        assertRs("80.00", s.getGrossFare());
        assertRs("4.00", s.getConvenienceFee());
        assertRs("4.00", s.getRiderCommission());
        assertRs("4.00", s.getDriverCommission());
        assertRs("12.00", s.getPlatformTotal());
        assertRs("68.00", s.getDriverNet());

        assertEquals(1, dues.accruals.size());
        assertRs("12.00", dues.accruals.get(0).amount());
    }

    @Test
    @DisplayName("A direct ride settled twice bills the driver once")
    void directSettlementIsIdempotent() {
        DirectRide ride = DirectRide.builder()
                .id(7L).driver(User.builder().id(99L).build())
                .estimatedFare(80.0).passengerCount(1).build();

        assertTrue(service.settleDirectRide(ride).isPresent());
        assertTrue(service.settleDirectRide(ride).isEmpty());

        assertEquals(1, dues.accruals.size());
    }

    @Test
    @DisplayName("Pooled and direct rides sharing an id settle independently")
    void rideKindDisambiguatesOverlappingIds() {
        Ride pooled = Ride.builder().id(7L)
                .driver(User.builder().id(99L).build()).build();
        DirectRide direct = DirectRide.builder().id(7L)
                .driver(User.builder().id(99L).build())
                .estimatedFare(80.0).passengerCount(1).build();

        assertTrue(service.settlePooledRide(pooled, List.of(passenger(1L, 5.0, 80.0))).isPresent());
        assertTrue(service.settleDirectRide(direct).isPresent(),
                "a direct ride with the same id is a different ride");

        assertEquals(2, savedSettlements.size());
        assertEquals(RideKind.POOLED, savedSettlements.get(0).getRideKind());
        assertEquals(RideKind.DIRECT, savedSettlements.get(1).getRideKind());
    }

    @Test
    @DisplayName("A ride nobody paid for is not settled and bills nothing")
    void zeroFareRideIsNotSettled() {
        assertTrue(service.settlePooledRide(pooledRide(), List.of()).isEmpty());
        assertTrue(service.settlePooledRide(
                pooledRide(), List.of(passenger(1L, 5.0, 0.0))).isEmpty());

        assertTrue(savedSettlements.isEmpty());
        assertTrue(dues.accruals.isEmpty());
    }

    @Test
    @DisplayName("Riders dropped mid-route are billed alongside those who rode to the end")
    void droppedPassengersAreIncluded() {
        Ride ride = pooledRide();
        RidePassenger droppedEarly = passenger(1L, 2.0, 20.0);
        droppedEarly.setStatus(RidePassengerStatus.DROPPED);

        RideSettlement s = service.settlePooledRide(
                ride, List.of(droppedEarly, passenger(2L, 8.0, 20.0))).orElseThrow();

        assertEquals(2, s.getPassengerCount());
        assertRs("40.00", s.getGrossFare());
        // 2 km of 10 → ₹8; 8 km → ₹32.
        assertEquals(8.0, droppedEarly.getFareShare(), 0.01);
    }

    /** Captures what the driver was billed, without a database. */
    private static class RecordingDuesService extends DriverDuesService {
        record Accrual(BigDecimal amount, String dedupeKey) {}

        final List<Accrual> accruals = new ArrayList<>();

        RecordingDuesService() {
            super(null, null, new BigDecimal("100"));
        }

        @Override
        public BigDecimal accrue(User driver, BigDecimal amount,
                                 String dedupeKey, RideSettlement settlement) {
            accruals.add(new Accrual(amount, dedupeKey));
            return amount;
        }
    }
}
