package com.backend.iniato.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Ride ride;

    @ManyToOne
    private User passenger;

    private double amount;
    private String status; // PENDING, SUCCESS, FAILED
    private String paymentMethod; // WALLET, RAZORPAY, STRIPE, CASH

    private LocalDateTime timestamp;
}

