package com.backend.iniato.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Revenue policy — every rate Iniato charges, in one place.
 * <p>
 * These are business numbers, not constants: the slab table and the commission
 * percentages are expected to be retuned without a code change, so they live in
 * {@code application.properties} under {@code iniato.fee}.
 *
 * <pre>
 *   iniato.fee.convenience.slabs=30:2,60:3,100:4,*:5
 *   iniato.fee.pool.driver-commission=0.01
 *   iniato.fee.direct.rider-commission=0.05
 *   iniato.fee.direct.driver-commission=0.05
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "iniato.fee")
@Getter
@Setter
public class FeeProperties {

    private Convenience convenience = new Convenience();
    private Pool pool = new Pool();
    private Direct direct = new Direct();

    /** Parsed form of {@link Convenience#slabs}, sorted ascending by threshold. */
    private List<Slab> parsedSlabs = List.of();

    @Getter
    @Setter
    public static class Convenience {
        /**
         * Comma-separated {@code upperBound:fee} pairs, with {@code *} as the
         * catch-all for anything above the last bound. Bounds are inclusive.
         */
        private String slabs = "30:2,60:3,100:4,*:5";
    }

    @Getter
    @Setter
    public static class Pool {
        /** Driver's cut of the pooled fare remaining after convenience fees. */
        private BigDecimal driverCommission = new BigDecimal("0.01");
    }

    @Getter
    @Setter
    public static class Direct {
        private BigDecimal riderCommission = new BigDecimal("0.05");
        private BigDecimal driverCommission = new BigDecimal("0.05");
    }

    /**
     * One convenience-fee band. A {@code null} {@link #upTo} marks the
     * catch-all band applied above every other bound.
     */
    public record Slab(BigDecimal upTo, BigDecimal fee) {
        public boolean covers(BigDecimal share) {
            return upTo == null || share.compareTo(upTo) <= 0;
        }
    }

    /**
     * Parse the slab string once at startup rather than on every fare.
     *
     * @throws IllegalStateException when the configured string is unusable —
     *         failing at boot is far safer than silently charging ₹0.
     */
    @PostConstruct
    public void parseSlabs() {
        List<Slab> bounded = new ArrayList<>();
        Slab catchAll = null;

        for (String entry : convenience.getSlabs().split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            String[] parts = trimmed.split(":");
            if (parts.length != 2) {
                throw new IllegalStateException(
                        "Malformed convenience-fee slab '" + trimmed + "' — expected 'bound:fee'");
            }
            String bound = parts[0].trim();
            BigDecimal fee = new BigDecimal(parts[1].trim());

            if ("*".equals(bound)) {
                catchAll = new Slab(null, fee);
            } else {
                bounded.add(new Slab(new BigDecimal(bound), fee));
            }
        }

        if (catchAll == null) {
            throw new IllegalStateException(
                    "Convenience-fee slabs must end with a '*:<fee>' catch-all band");
        }

        bounded.sort(Comparator.comparing(Slab::upTo));
        bounded.add(catchAll);
        this.parsedSlabs = List.copyOf(bounded);
    }
}
