package com.backend.iniato.entity;

import com.backend.iniato.enums.RideKind;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What Iniato earned on one completed ride, and what the driver kept.
 *
 * <p>This is the <b>idempotency anchor</b> for the whole revenue path. A pooled
 * ride can be completed through two different endpoints
 * ({@code /api/rides/{id}/complete} and {@code /api/routes/{id}/complete}), so
 * the unique constraint on {@code (ride_kind, ride_id)} — not application
 * logic — is what guarantees a driver is never charged twice for one ride.
 */
@Entity
@Table(
        name = "ride_settlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_ride",
                columnNames = {"ride_kind", "ride_id"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which ride table {@link #rideId} points into. Pooled and direct ride ids
     * overlap, so neither alone identifies a ride.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ride_kind", nullable = false, length = 16)
    private RideKind rideKind;

    /** Id within the table {@link #rideKind} names. Deliberately not an FK. */
    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    /** The fare the rider(s) actually paid, in total. */
    @Column(precision = 10, scale = 2)
    private BigDecimal grossFare;

    /** Sum of every rider's convenience fee. */
    @Column(precision = 10, scale = 2)
    private BigDecimal convenienceFee;

    /** Percentage cut attributed to the rider side (direct rides only). */
    @Column(precision = 10, scale = 2)
    private BigDecimal riderCommission;

    /** Percentage cut charged to the driver. */
    @Column(precision = 10, scale = 2)
    private BigDecimal driverCommission;

    /** What Iniato earned — the driver owes this, having collected cash. */
    @Column(precision = 10, scale = 2)
    private BigDecimal platformTotal;

    /** {@code grossFare - platformTotal}. */
    @Column(precision = 10, scale = 2)
    private BigDecimal driverNet;

    private Integer passengerCount;

    private LocalDateTime settledAt;

    @PrePersist
    void onCreate() {
        if (settledAt == null) settledAt = LocalDateTime.now();
    }
}
