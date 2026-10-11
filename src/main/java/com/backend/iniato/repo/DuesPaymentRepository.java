package com.backend.iniato.repo;

import com.backend.iniato.entity.DuesPayment;
import com.backend.iniato.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DuesPaymentRepository extends JpaRepository<DuesPayment, Long> {

    Optional<DuesPayment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<DuesPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    List<DuesPayment> findByDriverOrderByCreatedAtDesc(User driver);
}
