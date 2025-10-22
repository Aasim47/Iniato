package com.backend.iniato.repo;

import com.backend.iniato.entity.Fare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareRepository extends JpaRepository<Fare, Long> {}
