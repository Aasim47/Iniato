package com.backend.iniato.repo;

import com.backend.iniato.entity.DriverDuesAccount;
import com.backend.iniato.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriverDuesAccountRepository extends JpaRepository<DriverDuesAccount, Long> {

    Optional<DriverDuesAccount> findByDriver(User driver);

    /**
     * Lock the row before mutating the balance, mirroring the pessimistic
     * locking {@code RouteRepository.findByIdForUpdate} uses for seat counts.
     * A ride accrual and a Razorpay settlement can land concurrently; without
     * this one of them is lost.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DriverDuesAccount a where a.driver = :driver")
    Optional<DriverDuesAccount> findByDriverForUpdate(@Param("driver") User driver);
}
