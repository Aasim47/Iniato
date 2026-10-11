package com.backend.iniato.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What a driver currently owes Iniato.
 *
 * <p>Riders pay the driver in cash, so the platform's share of every fare
 * becomes a debt rather than a transfer. Once {@link #balance} reaches the
 * configured threshold the driver is blocked from taking new work until they
 * settle up — see {@code DriverDuesService}.
 *
 * <p>The balance is a running total maintained alongside an append-only
 * {@link DriverDuesTransaction} log; the log is the audit trail, this row is
 * the fast read. {@link #version} guards the two from drifting when a ride
 * accrual and a payment land at once.
 */
@Entity
@Table(name = "driver_dues_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDuesAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private User driver;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** Every rupee ever accrued, for the driver's own records. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lifetimeAccrued = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lifetimePaid = BigDecimal.ZERO;

    private LocalDateTime lastPaidAt;

    @Version
    private Long version;
}
