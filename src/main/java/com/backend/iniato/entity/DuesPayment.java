package com.backend.iniato.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A driver's Razorpay payment against their dues.
 *
 * <p>Created when the order is opened and updated when payment is confirmed.
 * Confirmation can arrive twice — once from the app calling verify, once from
 * the Razorpay webhook — so {@link #razorpayPaymentId} is unique and whichever
 * arrives first wins; the other is a no-op.
 */
@Entity
@Table(
        name = "dues_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dues_payment_order", columnNames = "razorpay_order_id"),
                @UniqueConstraint(name = "uk_dues_payment_payment", columnNames = "razorpay_payment_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuesPayment {

    /** Lifecycle of a dues payment attempt. */
    public enum Status { CREATED, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Dues owed when the order was created — context for support queries. */
    @Column(precision = 10, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "razorpay_order_id", nullable = false)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    private String razorpaySignature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.CREATED;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
