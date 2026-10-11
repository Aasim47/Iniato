package com.backend.iniato.services;

import com.backend.iniato.config.DuesExceededException;
import com.backend.iniato.entity.DriverDuesAccount;
import com.backend.iniato.entity.DriverDuesTransaction;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.DuesEntryType;
import com.backend.iniato.repo.DriverDuesAccountRepository;
import com.backend.iniato.repo.DriverDuesTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the rule that decides whether a driver can work: the dues threshold.
 *
 * <p>The repositories are mocked with stateful answers rather than fixed stubs,
 * because what matters here is arithmetic across repeated calls and the
 * idempotency of replayed events — behaviour a one-shot stub cannot express.
 * The transaction mock enforces the same unique {@code dedupe_key} the real
 * schema does, so the test fails the way production would.
 */
class DriverDuesServiceTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("100");

    private DriverDuesAccountRepository accounts;
    private DriverDuesTransactionRepository transactions;
    private DriverDuesService service;
    private User driver;

    /** The single account row the fake repository holds. */
    private DriverDuesAccount stored;
    private final List<DriverDuesTransaction> ledger = new ArrayList<>();
    private final Set<String> dedupeKeys = new HashSet<>();

    @BeforeEach
    void setUp() {
        stored = null;
        ledger.clear();
        dedupeKeys.clear();

        accounts = mock(DriverDuesAccountRepository.class);
        transactions = mock(DriverDuesTransactionRepository.class);

        when(accounts.findByDriver(any())).thenAnswer(i -> Optional.ofNullable(stored));
        when(accounts.findByDriverForUpdate(any())).thenAnswer(i -> Optional.ofNullable(stored));
        when(accounts.save(any())).thenAnswer((Answer<DriverDuesAccount>) i -> {
            stored = i.getArgument(0);
            if (stored.getId() == null) stored.setId(1L);
            return stored;
        });

        when(transactions.existsByDedupeKey(anyString()))
                .thenAnswer(i -> dedupeKeys.contains(i.<String>getArgument(0)));
        when(transactions.save(any())).thenAnswer((Answer<DriverDuesTransaction>) i -> {
            DriverDuesTransaction txn = i.getArgument(0);
            if (!dedupeKeys.add(txn.getDedupeKey())) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate dedupe_key " + txn.getDedupeKey());
            }
            ledger.add(txn);
            return txn;
        });

        service = new DriverDuesService(accounts, transactions, THRESHOLD);
        driver = User.builder().id(7L).phoneNumber("9000000000").build();
    }

    private static void assertRs(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected ₹" + expected + " but got ₹" + actual);
    }

    @Test
    @DisplayName("A driver who has never ridden owes nothing and can drive")
    void newDriverStartsClear() {
        assertRs("0.00", service.balanceFor(driver));
        assertFalse(service.isBlocked(driver));
        assertDoesNotThrow(() -> service.assertCanDrive(driver));
    }

    @Test
    @DisplayName("Accruals add up and are recorded in the ledger")
    void accrualsAccumulate() {
        service.accrue(driver, new BigDecimal("9.71"), "POOLED:1:ACCRUAL", null);
        service.accrue(driver, new BigDecimal("12.00"), "DIRECT:2:ACCRUAL", null);

        assertRs("21.71", service.balanceFor(driver));
        assertEquals(2, ledger.size());
        assertEquals(DuesEntryType.ACCRUAL, ledger.get(0).getType());
        assertRs("21.71", ledger.get(1).getBalanceAfter());
        assertRs("21.71", stored.getLifetimeAccrued());
    }

    @Test
    @DisplayName("Replaying the same ride's accrual charges the driver once")
    void accrualIsIdempotentOnDedupeKey() {
        service.accrue(driver, new BigDecimal("9.71"), "POOLED:1:ACCRUAL", null);
        service.accrue(driver, new BigDecimal("9.71"), "POOLED:1:ACCRUAL", null);
        service.accrue(driver, new BigDecimal("9.71"), "POOLED:1:ACCRUAL", null);

        assertRs("9.71", service.balanceFor(driver));
        assertEquals(1, ledger.size());
    }

    @Test
    @DisplayName("Exactly ₹100 blocks the driver — the threshold is inclusive")
    void thresholdIsInclusive() {
        service.accrue(driver, new BigDecimal("99.99"), "A", null);
        assertFalse(service.isBlocked(driver), "₹99.99 must still be drivable");
        assertDoesNotThrow(() -> service.assertCanDrive(driver));

        service.accrue(driver, new BigDecimal("0.01"), "B", null);
        assertRs("100.00", service.balanceFor(driver));
        assertTrue(service.isBlocked(driver), "exactly ₹100 must block");

        DuesExceededException blocked =
                assertThrows(DuesExceededException.class, () -> service.assertCanDrive(driver));
        assertRs("100.00", blocked.getBalance());
        assertRs("100.00", blocked.getThreshold());
        assertTrue(blocked.getMessage().contains("100.00"),
                "the driver should be told what they owe: " + blocked.getMessage());
    }

    @Test
    @DisplayName("Paying clears the balance and unblocks driving")
    void paymentClearsAndUnblocks() {
        service.accrue(driver, new BigDecimal("104.20"), "POOLED:1:ACCRUAL", null);
        assertTrue(service.isBlocked(driver));

        service.recordPayment(driver, new BigDecimal("104.20"), "order_1", "pay_1");

        assertRs("0.00", service.balanceFor(driver));
        assertFalse(service.isBlocked(driver));
        assertDoesNotThrow(() -> service.assertCanDrive(driver));
        assertRs("104.20", stored.getLifetimePaid());
        assertNotNull(stored.getLastPaidAt());
    }

    @Test
    @DisplayName("A payment confirmed twice — by verify and webhook — credits once")
    void paymentIsIdempotentOnPaymentId() {
        service.accrue(driver, new BigDecimal("104.20"), "POOLED:1:ACCRUAL", null);

        service.recordPayment(driver, new BigDecimal("104.20"), "order_1", "pay_1");
        service.recordPayment(driver, new BigDecimal("104.20"), "order_1", "pay_1");

        assertRs("0.00", service.balanceFor(driver));
        assertRs("104.20", stored.getLifetimePaid());
        assertEquals(1, ledger.stream()
                .filter(t -> t.getType() == DuesEntryType.PAYMENT).count());
    }

    @Test
    @DisplayName("Overpaying never leaves a credit balance")
    void overpaymentFloorsAtZero() {
        service.accrue(driver, new BigDecimal("50.00"), "A", null);
        service.recordPayment(driver, new BigDecimal("80.00"), "order_1", "pay_1");

        assertRs("0.00", service.balanceFor(driver));
    }

    @Test
    @DisplayName("A partial payment below the threshold puts the driver back on the road")
    void partialPaymentCanUnblock() {
        service.accrue(driver, new BigDecimal("120.00"), "A", null);
        service.recordPayment(driver, new BigDecimal("25.00"), "order_1", "pay_1");

        assertRs("95.00", service.balanceFor(driver));
        assertFalse(service.isBlocked(driver), "₹95 is under the ₹100 threshold");
    }

    @Test
    @DisplayName("Zero and negative accruals are ignored rather than recorded")
    void zeroAccrualIsANoOp() {
        service.accrue(driver, BigDecimal.ZERO, "A", null);
        service.accrue(driver, new BigDecimal("-5.00"), "B", null);

        assertRs("0.00", service.balanceFor(driver));
        assertTrue(ledger.isEmpty());
    }

    @Test
    @DisplayName("Status reports what must be paid to get back on the road")
    void statusReportsAmountToUnblock() {
        service.accrue(driver, new BigDecimal("132.50"), "A", null);

        var status = service.statusFor(driver);
        assertTrue(status.isBlocked());
        assertRs("132.50", status.getBalance());
        assertRs("100.00", status.getThreshold());
        assertRs("132.50", status.getAmountToUnblock());
    }
}
