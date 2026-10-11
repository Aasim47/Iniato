package com.backend.iniato.controller;

import com.backend.iniato.dto.EarningsSummaryResponse;
import com.backend.iniato.entity.RideSettlement;
import com.backend.iniato.entity.User;
import com.backend.iniato.repo.RideSettlementRepository;
import com.backend.iniato.services.UserLookupService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Gross, platform fee and net for a driver over a date range.
 *
 * <p>Answered from settled rides rather than recomputed from fares, so the
 * numbers always agree with what was actually billed.
 */
@RestController
@RequestMapping("/api/driver/earnings")
public class DriverEarningsController {

    private final RideSettlementRepository settlementRepository;
    private final UserLookupService userLookupService;

    public DriverEarningsController(RideSettlementRepository settlementRepository,
                                    UserLookupService userLookupService) {
        this.settlementRepository = settlementRepository;
        this.userLookupService = userLookupService;
    }

    /** The authenticated driver — shared cached lookup, one query at most. */
    private User currentDriver() {
        return userLookupService.currentUser();
    }

    /**
     * @param from inclusive start date; defaults to 30 days ago
     * @param to   inclusive end date; defaults to today
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DRIVER')")
    public ResponseEntity<EarningsSummaryResponse> summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();

        List<RideSettlement> settled = settlementRepository.findByDriverAndSettledAtBetween(
                currentDriver(), start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        BigDecimal gross = sum(settled, RideSettlement::getGrossFare);
        BigDecimal fee = sum(settled, RideSettlement::getPlatformTotal);

        return ResponseEntity.ok(EarningsSummaryResponse.builder()
                .gross(gross)
                .platformFee(fee)
                .net(gross.subtract(fee))
                .rideCount(settled.size())
                .build());
    }

    private static BigDecimal sum(List<RideSettlement> rows,
                                  java.util.function.Function<RideSettlement, BigDecimal> field) {
        return rows.stream()
                .map(field)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
