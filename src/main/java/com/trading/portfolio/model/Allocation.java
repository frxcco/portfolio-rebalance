package com.trading.portfolio.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable record representing the target allocation of an individual {@link Stock} within a portfolio.
 * <p>
 * Represents the target distribution weight of an asset (e.g., 40% META, 60% AAPL).
 * The target percentage is stored internally as a decimal fraction in the closed range [0.00, 1.00],
 * where {@code 0.40} represents 40% and {@code 1.00} represents 100%.
 * </p>
 *
 * @param stock the target stock asset, must not be null
 * @param targetPercentage target allocation expressed as a decimal fraction between 0.00 and 1.00 inclusive, must not be null
 */
public record Allocation(Stock stock, BigDecimal targetPercentage) {

    /**
     * Compact constructor validating constraints on stock and targetPercentage.
     *
     * @throws NullPointerException if {@code stock} or {@code targetPercentage} is null
     * @throws IllegalArgumentException if {@code targetPercentage} is outside the range [0.00, 1.00]
     */
    public Allocation {
        Objects.requireNonNull(stock, "Stock cannot be null");
        Objects.requireNonNull(targetPercentage, "Target percentage cannot be null");
        if (targetPercentage.compareTo(BigDecimal.ZERO) < 0 || targetPercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Target percentage must be between 0.0 and 1.0 (inclusive), got: " + targetPercentage);
        }
    }

    /**
     * Convenience constructor accepting a {@code double} target percentage as a decimal fraction [0.00, 1.00].
     *
     * @param stock the target stock asset, must not be null
     * @param targetPercentage target fraction in [0.00, 1.00]
     */
    public Allocation(Stock stock, double targetPercentage) {
        this(stock, BigDecimal.valueOf(targetPercentage));
    }

    /**
     * Factory method creating an {@code Allocation} from a {@link BigDecimal} percentage value.
     * <p>
     * Normalizes inputs flexibly:
     * <ul>
     *   <li>Values greater than 1.0 (e.g., 40 for 40%, 100 for 100%) are treated as base 100 percentages
     *       and normalized by dividing by 100 (e.g., 40 becomes 0.40).</li>
     *   <li>Values between 0.0 and 1.0 (inclusive) are treated directly as fractional representations (e.g., 0.40).</li>
     * </ul>
     * </p>
     *
     * @param stock the target stock asset, must not be null
     * @param percentage target percentage either in [0.0, 1.0] or (1.0, 100.0]
     * @return a new validated {@link Allocation} instance
     * @throws NullPointerException if stock or percentage is null
     * @throws IllegalArgumentException if percentage is negative or normalized value exceeds 1.0
     */
    public static Allocation ofPercentage(Stock stock, BigDecimal percentage) {
        Objects.requireNonNull(percentage, "Target percentage cannot be null");
        BigDecimal normalized = percentage.compareTo(BigDecimal.ONE) > 0
                ? percentage.movePointLeft(2)
                : percentage;
        return new Allocation(stock, normalized);
    }

    /**
     * Factory method creating an {@code Allocation} from a {@code double} percentage value.
     * <p>
     * Normalizes inputs in base 100 (&gt; 1.0) or base 1 (&le; 1.0).
     * </p>
     *
     * @param stock the target stock asset, must not be null
     * @param percentage target percentage either in [0.0, 1.0] or (1.0, 100.0]
     * @return a new validated {@link Allocation} instance
     */
    public static Allocation ofPercentage(Stock stock, double percentage) {
        return ofPercentage(stock, BigDecimal.valueOf(percentage));
    }

    /**
     * Returns the target percentage formatted in base 100 percentage points (e.g., 40.00 for 0.40).
     *
     * @return percentage in points [0.0, 100.0]
     */
    public BigDecimal getPercentagePoints() {
        return targetPercentage.movePointRight(2);
    }
}
