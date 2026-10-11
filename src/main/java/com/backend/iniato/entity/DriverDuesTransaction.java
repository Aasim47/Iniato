package com.backend.iniato.entity;

import com.backend.iniato.enums.DuesEntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One movement on a driver's dues balance — append-only.
 *
 * <p>This is the audit trail behind {@link DriverDuesAccount#getBalance()}: a
 * driver disputing what they owe should be answerable from these rows alone.
 *
 * <p>{@link #dedupeKey} is the idempotency guard. Every event that can move the
 * balance derives a stable key ({@code POOLED:412:ACCRUAL},
 * {@code PAY:pay_Nxyz}), so a replayed ride completion or a Razorpay webhook
 * arriving twice hits the unique constraint instead of double-charging.
 */
@Entity
@Table(
        name = "driver_dues_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dues_txn_dedupe", columnNames = "dedupe_key")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDuesTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private DriverDuesAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DuesEntryType type;

    /** Signed: positive raises the debt, negative clears it. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Balance after this entry — lets the ledger be read without replaying it. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    /** The ride that caused an {@link DuesEntryType#ACCRUAL}, when there was one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private RideSettlement settlement;

    /** Stable key for the causing event; see the class note. */
    @Column(name = "dedupe_key", nullable = false, length = 128)
    private String dedupeKey;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    private String note;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
