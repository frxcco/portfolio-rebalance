package com.trading.portfolio.model;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * Domain entity representing a financial stock / equity asset.
 * <p>
 * A stock is identified uniquely by its ticker symbol (e.g., "AAPL", "GOOGL").
 * Symbols are canonicalized to uppercase and trimmed to avoid inconsistencies.
 * Each stock maintains its latest market price, satisfying the challenge requirement:
 * <i>"Assume each Stock has a 'Current Price' method that receives the last available price."</i>
 * </p>
 */
public class Stock {

    /**
     * Unique market ticker symbol (canonicalized to uppercase and trimmed).
     */
    private final String symbol;

    /**
     * Human-readable company or asset name.
     */
    private final String name;

    /**
     * Latest market price of the stock.
     */
    private BigDecimal currentPrice;

    /**
     * Constructs a Stock instance without an initial price.
     *
     * @param symbol unique ticker symbol, cannot be null or empty
     * @param name company or asset name, cannot be null or empty
     * @throws IllegalArgumentException if symbol or name is null or blank
     */
    public Stock(String symbol, String name) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock symbol cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock name cannot be null or empty");
        }
        this.symbol = symbol.trim().toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.currentPrice = null;
    }

    /**
     * Constructs a Stock instance with an initial market price.
     *
     * @param symbol unique ticker symbol, cannot be null or empty
     * @param name company or asset name, cannot be null or empty
     * @param initialPrice initial market price (if not null, validated and set via {@link #updateCurrentPrice(BigDecimal)})
     * @throws IllegalArgumentException if symbol or name is null/blank, or if initialPrice is non-positive
     */
    public Stock(String symbol, String name, BigDecimal initialPrice) {
        this(symbol, name);
        if (initialPrice != null) {
            updateCurrentPrice(initialPrice);
        }
    }

    /**
     * Updates the current market price with the latest available price quote.
     * <p>
     * Fulfills the domain requirement: "Assume each Stock has a 'Current Price' method
     * that receives the last available price".
     * </p>
     *
     * @param price latest price quote, must not be null and must be strictly positive (&gt; 0)
     * @throws IllegalArgumentException if price is null or less than or equal to zero
     */
    public void updateCurrentPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be strictly positive");
        }
        this.currentPrice = price;
    }

    /**
     * Returns the current market price of the stock.
     *
     * @return current price as {@link BigDecimal}
     * @throws IllegalStateException if the price has not yet been set
     */
    public BigDecimal getCurrentPrice() {
        if (this.currentPrice == null) {
            throw new IllegalStateException("Price has not been set for stock: " + symbol);
        }
        return this.currentPrice;
    }

    /**
     * Checks whether a market price has been set for this stock.
     *
     * @return {@code true} if current price is present, {@code false} otherwise
     */
    public boolean hasPrice() {
        return this.currentPrice != null;
    }

    /**
     * Gets the ticker symbol.
     *
     * @return canonicalized ticker symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the company or asset name.
     *
     * @return asset name
     */
    public String getName() {
        return name;
    }

    /**
     * Two Stock objects are considered equal if they have the same ticker symbol,
     * normalized to uppercase using {@link Locale#ROOT}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Stock other = (Stock) o;
        return this.symbol.toUpperCase(Locale.ROOT).equals(other.symbol.toUpperCase(Locale.ROOT));
    }

    /**
     * Hash code based on the normalized ticker symbol.
     */
    @Override
    public int hashCode() {
        return Objects.hash(symbol.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "Stock{" +
                "symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", currentPrice=" + (currentPrice != null ? currentPrice : "N/A") +
                '}';
    }
}
