package com.trading.portfolio.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable record representing a holding position of a specific {@link Stock} in a portfolio.
 * <p>
 * Encapsulates the target stock and the quantity of shares held (supporting fractional shares).
 * Provides methods for calculating current market value based on the stock's latest market price,
 * as well as immutable functional methods for increasing or decreasing the position.
 * </p>
 *
 * @param stock the stock asset held in this position, must not be null
 * @param shares the number of shares held (non-negative, supports fractional quantities), must not be null
 */
public record PortfolioPosition(Stock stock, BigDecimal shares) {

    /**
     * Compact constructor validating constraints on stock and shares.
     *
     * @throws NullPointerException if {@code stock} or {@code shares} is null
     * @throws IllegalArgumentException if {@code shares} is negative (&lt; 0)
     */
    public PortfolioPosition {
        Objects.requireNonNull(stock, "Stock cannot be null");
        Objects.requireNonNull(shares, "Shares cannot be null");
        if (shares.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Shares cannot be negative, got: " + shares);
        }
    }

    /**
     * Convenience constructor accepting shares as a {@code double}.
     *
     * @param stock the stock asset held in this position, must not be null
     * @param shares quantity of shares as a double (must be non-negative)
     */
    public PortfolioPosition(Stock stock, double shares) {
        this(stock, BigDecimal.valueOf(shares));
    }

    /**
     * Convenience constructor accepting shares as a {@code long}.
     *
     * @param stock the stock asset held in this position, must not be null
     * @param shares quantity of shares as a long (must be non-negative)
     */
    public PortfolioPosition(Stock stock, long shares) {
        this(stock, BigDecimal.valueOf(shares));
    }

    /**
     * Calculates the current market value of this position.
     * <p>
     * Calculated as: {@code shares * stock.getCurrentPrice()}, scaled to 2 decimal places using
     * banker's rounding ({@link RoundingMode#HALF_EVEN}).
     * </p>
     *
     * @return current market value of the held position as {@link BigDecimal} with scale 2
     * @throws IllegalStateException if the stock does not have a current price established
     */
    public BigDecimal getMarketValue() {
        if (!stock.hasPrice()) {
            throw new IllegalStateException("Price has not been set for stock: " + stock.getSymbol());
        }
        return shares.multiply(stock.getCurrentPrice()).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Returns a new immutable {@code PortfolioPosition} with the specified additional shares added.
     *
     * @param additionalShares number of shares to add, must not be null and must be non-negative
     * @return a new {@link PortfolioPosition} instance with the updated shares count
     * @throws NullPointerException if {@code additionalShares} is null
     * @throws IllegalArgumentException if {@code additionalShares} is negative
     */
    public PortfolioPosition addShares(BigDecimal additionalShares) {
        Objects.requireNonNull(additionalShares, "Additional shares cannot be null");
        if (additionalShares.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Additional shares cannot be negative, got: " + additionalShares);
        }
        return new PortfolioPosition(stock, this.shares.add(additionalShares));
    }

    /**
     * Returns a new immutable {@code PortfolioPosition} with the specified additional shares added.
     *
     * @param additionalShares number of shares to add as a {@code double}
     * @return a new {@link PortfolioPosition} instance with the updated shares count
     */
    public PortfolioPosition addShares(double additionalShares) {
        return addShares(BigDecimal.valueOf(additionalShares));
    }

    /**
     * Returns a new immutable {@code PortfolioPosition} with the specified shares subtracted.
     *
     * @param sharesToSubtract number of shares to subtract, must not be null, must be non-negative,
     *                         and cannot exceed current held shares
     * @return a new {@link PortfolioPosition} instance with the updated shares count
     * @throws NullPointerException if {@code sharesToSubtract} is null
     * @throws IllegalArgumentException if {@code sharesToSubtract} is negative or greater than current held shares
     */
    public PortfolioPosition subtractShares(BigDecimal sharesToSubtract) {
        Objects.requireNonNull(sharesToSubtract, "Shares to subtract cannot be null");
        if (sharesToSubtract.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Shares to subtract cannot be negative, got: " + sharesToSubtract);
        }
        if (sharesToSubtract.compareTo(this.shares) > 0) {
            throw new IllegalArgumentException(
                    "Cannot subtract " + sharesToSubtract + " shares from position holding only " + this.shares + " shares");
        }
        return new PortfolioPosition(stock, this.shares.subtract(sharesToSubtract));
    }

    /**
     * Returns a new immutable {@code PortfolioPosition} with the specified shares subtracted.
     *
     * @param sharesToSubtract number of shares to subtract as a {@code double}
     * @return a new {@link PortfolioPosition} instance with the updated shares count
     */
    public PortfolioPosition subtractShares(double sharesToSubtract) {
        return subtractShares(BigDecimal.valueOf(sharesToSubtract));
    }
}
