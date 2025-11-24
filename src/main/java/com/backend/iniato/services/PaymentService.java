package com.backend.iniato.services;

import com.backend.iniato.dto.PaymentRequestDTO;
import com.backend.iniato.dto.PaymentResponseDTO;
import com.backend.iniato.entity.Payment;
import com.backend.iniato.entity.Ride;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.PaymentRepository;
import com.backend.iniato.repo.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RideRepository rideRepository;

    /**
     * Split fare equally among passengers for a completed shared ride
     */
    public PaymentResponseDTO splitSharedFare(PaymentRequestDTO request) {
        Ride ride = rideRepository.findById(request.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        int passengerCount = ride.getPassengers().size();
        double splitAmount = request.getAmount() / passengerCount;

        for (User passenger : ride.getPassengers()) {
            Payment payment = Payment.builder()
                    .ride(ride)
                    .passenger(passenger)
                    .amount(splitAmount)
                    .paymentMethod(request.getPaymentMethod())
                    .status("SUCCESS")
                    .timestamp(LocalDateTime.now())
                    .build();

            paymentRepository.save(payment);
        }

        return new PaymentResponseDTO("SUCCESS",
                "Fare split among " + passengerCount + " passengers",
                request.getAmount());
    }
}
