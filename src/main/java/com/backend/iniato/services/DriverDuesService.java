package com.backend.iniato.services;

import com.backend.iniato.config.DuesExceededException;
import com.backend.iniato.dto.DuesLedgerEntry;
import com.backend.iniato.dto.DuesStatusResponse;
import com.backend.iniato.entity.DriverDuesAccount;
import com.backend.iniato.entity.DriverDuesTransaction;
import com.backend.iniato.entity.RideSettlement;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.DuesEntryType;
import com.backend.iniato.repo.DriverDuesAccountRepository;
import com.backend.iniato.repo.DriverDuesTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tracks what each driver owes Iniato, and refuses them work when it gets too
 * high.
 *
 * <p>Riders pay the driver in cash, so the platform never holds the money — its
 * share accrues here as a debt instead. A driver works freely until the balance
 * reaches {@code iniato.dues.threshold}; past that they must settle up via
 * Razorpay before going online, creating a route, or accepting and starting
 * rides. Finishing an in-flight ride is always allowed: blocking a driver
 * mid-trip would strand a rider, and completing the ride is what lets them pay.
 */
@Service
public class DriverDuesService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final DriverDuesAccountRepository accountRepository;
    private final DriverDuesTransactionRepository transactionRepository;
    private final BigDecimal threshold;

    public DriverDuesService(DriverDuesAccountRepository accountRepository,
                             DriverDuesTransactionRepository transactionRepository,
                             @Value("${iniato.dues.threshold:100}") BigDecimal threshold) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.threshold = threshold.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    /** The balance at which driving stops. */
    public BigDecimal getThreshold() {
        return threshold;
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceFor(User driver) {
        return accountRepository.findByDriver(driver)
                .map(DriverDuesAccount::getBalance)
                .orElse(zero());
    }

    /** True once the driver owes at least the threshold. Exactly ₹100 blocks. */
    @Transactional(readOnly = true)
    public boolean isBlocked(User driver) {
        return balanceFor(driver).compareTo(threshold) >= 0;
    }

    /**
     * Gate for every action that starts new work.
     *
     * @throws DuesExceededException when the driver owes too much — mapped to
     *         HTTP 402 by {@code GlobalExceptionHandler}
     */
    @Transactional(readOnly = true)
    public void assertCanDrive(User driver) {
        BigDecimal balance = balanceFor(driver);
        if (balance.compareTo(threshold) >= 0) {
            throw new DuesExceededException(balance, threshold);
        }
    }

    @Transactional(readOnly = true)
    public DuesStatusResponse statusFor(User driver) {
        DriverDuesAccount account = accountRepository.findByDriver(driver).orElse(null);

        BigDecimal balance = account != null ? account.getBalance() : zero();
        boolean blocked = balance.compareTo(threshold) >= 0;

        return DuesStatusResponse.builder()
                .balance(balance)
                .threshold(threshold)
                .blocked(blocked)
                // Clearing the whole balance is the only way back under the
                // threshold that leaves no immediate re-block.
                .amountToUnblock(blocked ? balance : zero())
                .lifetimeAccrued(account != null ? account.getLifetimeAccrued() : zero())
                .lifetimePaid(account != null ? account.getLifetimePaid() : zero())
                .lastPaidAt(account != null ? account.getLastPaidAt() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<DriverDuesTransaction> ledgerFor(User driver, Pageable pageable) {
        return accountRepository.findByDriver(driver)
                .map(a -> transactionRepository.findByAccountOrderByCreatedAtDesc(a, pageable))
                .orElse(Page.empty(pageable));
    }

    /**
     * The driver's ledger, already flattened for the wire.
     *
     * <p>The mapping belongs here, not in the controller. {@link #ledgerFor}
     * hands back entities whose {@code settlement} association is lazy, and the
     * controller flattened them after this transaction had committed and the
     * session had closed — so reading {@code settlement.getRideId()} depended
     * entirely on open-in-view. With that off, every ACCRUAL row (the ones that
     * have a settlement) would throw. Mapping inside the transaction fixes it
     * for good rather than re-enabling a setting that costs extra queries on
     * every response.
     */
    @Transactional(readOnly = true)
    public Page<DuesLedgerEntry> ledgerEntriesFor(User driver, Pageable pageable) {
        return ledgerFor(driver, pageable).map(DriverDuesService::toLedgerEntry);
    }

    private static DuesLedgerEntry toLedgerEntry(DriverDuesTransaction txn) {
        RideSettlement settlement = txn.getSettlement();
        return DuesLedgerEntry.builder()
                .id(txn.getId())
                .type(txn.getType().name())
                .amount(txn.getAmount())
                .balanceAfter(txn.getBalanceAfter())
                .note(txn.getNote())
                .createdAt(txn.getCreatedAt())
                .rideId(settlement != null ? settlement.getRideId() : null)
                .rideKind(settlement != null && settlement.getRideKind() != null
                        ? settlement.getRideKind().name() : null)
                .build();
    }

    /**
     * Add a completed ride's platform cut to what the driver owes.
     *
     * <p>Idempotent on {@code dedupeKey}: a ride completed twice — which is
     * possible, since a pooled ride has two completion endpoints — accrues
     * once. Runs in the caller's transaction so the accrual commits with the
     * settlement or not at all.
     *
     * @return the balance after this accrual
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public BigDecimal accrue(User driver, BigDecimal amount,
                             String dedupeKey, RideSettlement settlement) {
        BigDecimal delta = money(amount);
        if (delta.signum() <= 0) return balanceFor(driver);

        if (transactionRepository.existsByDedupeKey(dedupeKey)) {
            return balanceFor(driver);
        }

        DriverDuesAccount account = lockedAccountFor(driver);
        BigDecimal balanceAfter = money(account.getBalance().add(delta));

        account.setBalance(balanceAfter);
        account.setLifetimeAccrued(money(account.getLifetimeAccrued().add(delta)));
        accountRepository.save(account);

        try {
            transactionRepository.save(DriverDuesTransaction.builder()
                    .account(account)
                    .type(DuesEntryType.ACCRUAL)
                    .amount(delta)
                    .balanceAfter(balanceAfter)
                    .settlement(settlement)
                    .dedupeKey(dedupeKey)
                    .note("Platform fee on ride")
                    .build());
        } catch (DataIntegrityViolationException alreadyAccrued) {
            // Another thread won the race on the same event. The unique
            // constraint — not the pre-check above — is the real guarantee.
            throw alreadyAccrued;
        }

        return balanceAfter;
    }

    /**
     * Clear dues after a confirmed Razorpay payment.
     *
     * <p>Idempotent on the payment id, because confirmation arrives twice by
     * design: once from the app calling verify, once from the webhook.
     *
     * @return the balance after payment
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public BigDecimal recordPayment(User driver, BigDecimal amount,
                                    String orderId, String paymentId) {
        BigDecimal paid = money(amount);
        String dedupeKey = "PAY:" + paymentId;

        if (paid.signum() <= 0 || transactionRepository.existsByDedupeKey(dedupeKey)) {
            return balanceFor(driver);
        }

        DriverDuesAccount account = lockedAccountFor(driver);
        // Never let an overpayment drive the balance negative — a credit
        // balance would quietly fund future rides.
        BigDecimal balanceAfter = money(account.getBalance().subtract(paid)).max(zero());

        account.setBalance(balanceAfter);
        account.setLifetimePaid(money(account.getLifetimePaid().add(paid)));
        account.setLastPaidAt(java.time.LocalDateTime.now());
        accountRepository.save(account);

        transactionRepository.save(DriverDuesTransaction.builder()
                .account(account)
                .type(DuesEntryType.PAYMENT)
                .amount(paid.negate())
                .balanceAfter(balanceAfter)
                .dedupeKey(dedupeKey)
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .note("Dues payment")
                .build());

        return balanceAfter;
    }

    /**
     * The driver's account, locked for update, created on first use.
     * <p>
     * Drivers who registered before dues existed have no row; creating it
     * lazily avoids a backfill over the whole table.
     */
    private DriverDuesAccount lockedAccountFor(User driver) {
        return accountRepository.findByDriverForUpdate(driver)
                .orElseGet(() -> accountRepository.save(
                        DriverDuesAccount.builder().driver(driver).build()));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
