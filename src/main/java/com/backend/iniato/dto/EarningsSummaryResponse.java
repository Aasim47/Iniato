package com.backend.iniato.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * What a driver earned over a period, and what Iniato took.
 *
 * <p>{@link #gross} is the cash the driver collected from riders;
 * {@link #net} is what they keep once {@link #platformFee} is settled.
 */
@Data
@Builder
public class EarningsSummaryResponse {

    private BigDecimal gross;

    /** Convenience fees plus commission — what the driver owes on these rides. */
    private BigDecimal platformFee;

    private BigDecimal net;

    private int rideCount;
}
