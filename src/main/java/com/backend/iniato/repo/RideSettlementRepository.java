package com.backend.iniato.repo;

import com.backend.iniato.entity.RideSettlement;
import com.backend.iniato.entity.User;
import com.backend.iniato.enums.RideKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RideSettlementRepository extends JpaRepository<RideSettlement, Long> {

    /** The settled-already check that makes the completion paths idempotent. */
    Optional<RideSettlement> findByRideKindAndRideId(RideKind rideKind, Long rideId);

    boolean existsByRideKindAndRideId(RideKind rideKind, Long rideId);

    List<RideSettlement> findByDriverOrderBySettledAtDesc(User driver);

    List<RideSettlement> findByDriverAndSettledAtBetween(
            User driver, LocalDateTime from, LocalDateTime to);
}
