package com.backend.iniato.repo;

import com.backend.iniato.entity.DriverDuesAccount;
import com.backend.iniato.entity.DriverDuesTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverDuesTransactionRepository
        extends JpaRepository<DriverDuesTransaction, Long> {

    Page<DriverDuesTransaction> findByAccountOrderByCreatedAtDesc(
            DriverDuesAccount account, Pageable pageable);

    /** Pre-check for a replayed event; the unique constraint is the real guard. */
    boolean existsByDedupeKey(String dedupeKey);
}
