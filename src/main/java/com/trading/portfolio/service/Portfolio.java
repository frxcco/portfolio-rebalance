package com.trading.portfolio.service;

import com.trading.portfolio.model.Allocation;
import com.trading.portfolio.model.PortfolioPosition;
import com.trading.portfolio.model.RebalancePlan;
import com.trading.portfolio.model.Stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Root aggregate domain entity representing an investment {@code Portfolio}.
 * <p>
 * This class serves as the central orchestrator and stateful container fulfilling the core business requirement:
 * <blockquote>
 * <i>"Construct a simple Portfolio class that has a collection of Stocks. Assume each Stock has a
 * 'Current Price' method that receives the last available price. Also, the Portfolio class has a
 * collection of 'allocated' Stocks that represents the distribution of the Stocks the Portfolio is aiming
 * (i.e. 40% META, 60% APPL). Provide a portfolio rebalance method to know which Stocks should be sold and
 * which ones should be bought to have a balanced Portfolio based on the portfolio's allocation."</i>
 * </blockquote>
 * </p>
 *
 * <h3>Design &amp; Architecture Principles</h3>
 * <ul>
 *   <li><b>Root Aggregate (DDD):</b> Encapsulates both actual held positions ({@link PortfolioPosition})
 *       and desired target asset weights ({@link Allocation}), protecting internal invariants.</li>
 *   <li><b>Dependency Injection &amp; Extensibility:</b> Rebalancing logic is delegated to a configurable
 *       {@link RebalanceEngine} strategy (defaulting to {@link DefaultRebalanceEngine}), enabling seamless
 *       unit testing and pluggable financial execution models.</li>
 *   <li><b>Position Aggregation:</b> Adding positions for existing assets automatically accumulates shares,
 *       maintaining unique holdings keyed by canonical uppercase stock ticker symbols.</li>
 *   <li><b>Strict Allocation Invariant:</b> Target allocations must mathematically sum to exactly 100% ({@code 1.00})
 *       without duplicate symbols, preventing invalid portfolio states.</li>
 *   <li><b>Immutability of Views:</b> Internal state maps are exposed only via unmodifiable map views
 *       ({@link Collections#unmodifiableMap(Map)}) to prevent unauthorized external mutation.</li>
 * </ul>
 */
public class Portfolio {

    /**
     * Internal map storing current stock positions keyed by canonical uppercase ticker symbol.
     */
    private final Map<String, PortfolioPosition> positions = new LinkedHashMap<>();

    /**
     * Internal map storing target allocations keyed by canonical uppercase ticker symbol.
     */
    private final Map<String, Allocation> allocations = new LinkedHashMap<>();

    /**
     * Rebalancing algorithmic engine responsible for trade plan calculation.
     */
    private final RebalanceEngine rebalanceEngine;

    /**
     * Constructs a new {@code Portfolio} with the standard {@link DefaultRebalanceEngine}.
     */
    public Portfolio() {
        this(new DefaultRebalanceEngine());
    }

    /**
     * Constructs a new {@code Portfolio} with a custom {@link RebalanceEngine}.
     *
     * @param rebalanceEngine the rebalancing strategy engine to use, must not be null
     * @throws NullPointerException if {@code rebalanceEngine} is null
     */
    public Portfolio(RebalanceEngine rebalanceEngine) {
        this.rebalanceEngine = Objects.requireNonNull(rebalanceEngine, "RebalanceEngine cannot be null");
    }

    // =========================================================================
    // Position Management Methods
    // =========================================================================

    /**
     * Adds a position holding for the specified {@link Stock} with the given quantity of shares.
     * <p>
     * If a position for this stock ticker already exists, the specified shares are added to the existing
     * holding (accumulating quantity). If no position exists, a new holding is created.
     * </p>
     *
     * @param stock the stock asset to add, must not be null
     * @param shares the number of shares to add (must be strictly positive &gt; 0)
     * @throws NullPointerException if {@code stock} or {@code shares} is null
     * @throws IllegalArgumentException if {@code shares} is less than or equal to zero
     */
    public void addPosition(Stock stock, BigDecimal shares) {
        Objects.requireNonNull(stock, "Stock cannot be null");
        Objects.requireNonNull(shares, "Shares cannot be null");
        if (shares.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Shares must be strictly positive, got: " + shares);
        }

        String symbol = stock.getSymbol();
        PortfolioPosition existing = positions.get(symbol);
        if (existing != null) {
            positions.put(symbol, existing.addShares(shares));
        } else {
            positions.put(symbol, new PortfolioPosition(stock, shares));
        }
    }

    /**
     * Convenience overload to add a position holding using a {@code double} shares value.
     *
     * @param stock the stock asset to add, must not be null
     * @param shares the number of shares as double (must be strictly positive &gt; 0)
     * @throws NullPointerException if {@code stock} is null
     * @throws IllegalArgumentException if {@code shares} is less than or equal to zero
     */
    public void addPosition(Stock stock, double shares) {
        addPosition(stock, BigDecimal.valueOf(shares));
    }

    /**
     * Convenience overload to add a position holding using a {@code long} shares value.
     *
     * @param stock the stock asset to add, must not be null
     * @param shares the number of shares as long (must be strictly positive &gt; 0)
     * @throws NullPointerException if {@code stock} is null
     * @throws IllegalArgumentException if {@code shares} is less than or equal to zero
     */
    public void addPosition(Stock stock, long shares) {
        addPosition(stock, BigDecimal.valueOf(shares));
    }

    /**
     * Retrieves the position holding for the given ticker symbol, if present.
     *
     * @param symbol ticker symbol to search for (case-insensitive)
     * @return an {@link Optional} containing the {@link PortfolioPosition} if present, or empty
     */
    public Optional<PortfolioPosition> getPosition(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(positions.get(symbol.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * Retrieves the position holding for the given {@link Stock}, if present.
     *
     * @param stock the stock asset to search for
     * @return an {@link Optional} containing the {@link PortfolioPosition} if present, or empty
     */
    public Optional<PortfolioPosition> getPosition(Stock stock) {
        return stock != null ? getPosition(stock.getSymbol()) : Optional.empty();
    }

    /**
     * Returns an unmodifiable view of all current holdings in this portfolio.
     *
     * @return unmodifiable map of positions keyed by uppercase ticker symbol
     */
    public Map<String, PortfolioPosition> getPositions() {
        return Collections.unmodifiableMap(positions);
    }

    /**
     * Clears all held positions from this portfolio.
     */
    public void clearPositions() {
        positions.clear();
    }

    /**
     * Indicates whether this portfolio currently holds any positions.
     *
     * @return {@code true} if at least one position exists, {@code false} otherwise
     */
    public boolean hasPositions() {
        return !positions.isEmpty();
    }

    // =========================================================================
    // Allocation Management Methods
    // =========================================================================

    /**
     * Sets the complete collection of target allocations for this portfolio.
     * <p>
     * Enforces strict validation invariants:
     * <ul>
     *   <li>The collection cannot be null or empty.</li>
     *   <li>No duplicate ticker symbols are allowed within the collection.</li>
     *   <li>The sum of all target percentages must equal exactly 100% ({@code 1.00}).</li>
     * </ul>
     * If all validations pass, existing allocations are cleared and replaced atomically.
     * </p>
     *
     * @param targetAllocations collection of target allocations, must not be null or empty
     * @throws IllegalArgumentException if {@code targetAllocations} is null, empty, contains duplicates,
     *                                  or does not sum to exactly 1.00 (100%)
     */
    public void setAllocations(Collection<Allocation> targetAllocations) {
        if (targetAllocations == null || targetAllocations.isEmpty()) {
            throw new IllegalArgumentException("Target allocations cannot be null or empty");
        }

        Map<String, Allocation> newAllocations = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (Allocation allocation : targetAllocations) {
            if (allocation == null) {
                throw new IllegalArgumentException("Allocation element cannot be null");
            }
            String symbol = allocation.stock().getSymbol();
            if (newAllocations.containsKey(symbol)) {
                throw new IllegalArgumentException("Duplicate allocation for stock symbol: " + symbol);
            }
            newAllocations.put(symbol, allocation);
            sum = sum.add(allocation.targetPercentage());
        }

        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Total target allocation must equal 100% (1.00), got: " + sum);
        }

        allocations.clear();
        allocations.putAll(newAllocations);
    }

    /**
     * Convenience varargs overload for setting target allocations.
     *
     * @param targetAllocations array of target allocations, must not be null or empty
     * @throws IllegalArgumentException if {@code targetAllocations} is null, empty, contains duplicates,
     *                                  or does not sum to exactly 1.00 (100%)
     */
    public void setAllocations(Allocation... targetAllocations) {
        if (targetAllocations == null) {
            throw new IllegalArgumentException("Target allocations cannot be null");
        }
        setAllocations(Arrays.asList(targetAllocations));
    }

    /**
     * Helper method to set or update an individual target allocation for a stock.
     * <p>
     * Supports fractional percentages in [0.00, 1.00] as well as base-100 percentage values
     * (e.g., 40 for 40%) via automatic normalization through {@link Allocation#ofPercentage(Stock, BigDecimal)}.
     * When configuring allocations individually, call {@link #validateAllocations()} before rebalancing.
     * </p>
     *
     * @param stock the stock asset, must not be null
     * @param targetPercentage target percentage (fractional or percentage points), must not be null
     * @throws NullPointerException if {@code stock} or {@code targetPercentage} is null
     * @throws IllegalArgumentException if target percentage is negative or exceeds valid bounds
     */
    public void setAllocation(Stock stock, BigDecimal targetPercentage) {
        Objects.requireNonNull(stock, "Stock cannot be null");
        Objects.requireNonNull(targetPercentage, "Target percentage cannot be null");
        allocations.put(stock.getSymbol(), Allocation.ofPercentage(stock, targetPercentage));
    }

    /**
     * Convenience overload to set an individual target allocation using a {@code double} percentage.
     *
     * @param stock the stock asset, must not be null
     * @param targetPercentage target percentage as double
     */
    public void setAllocation(Stock stock, double targetPercentage) {
        setAllocation(stock, BigDecimal.valueOf(targetPercentage));
    }

    /**
     * Sets or updates an individual target allocation.
     *
     * @param allocation target allocation record, must not be null
     * @throws NullPointerException if {@code allocation} is null
     */
    public void setAllocation(Allocation allocation) {
        Objects.requireNonNull(allocation, "Allocation cannot be null");
        allocations.put(allocation.stock().getSymbol(), allocation);
    }

    /**
     * Retrieves the target allocation for the given ticker symbol, if present.
     *
     * @param symbol ticker symbol to search for (case-insensitive)
     * @return an {@link Optional} containing the {@link Allocation} if present, or empty
     */
    public Optional<Allocation> getAllocation(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(allocations.get(symbol.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * Retrieves the target allocation for the given {@link Stock}, if present.
     *
     * @param stock the stock asset to search for
     * @return an {@link Optional} containing the {@link Allocation} if present, or empty
     */
    public Optional<Allocation> getAllocation(Stock stock) {
        return stock != null ? getAllocation(stock.getSymbol()) : Optional.empty();
    }

    /**
     * Returns an unmodifiable view of all configured target allocations.
     *
     * @return unmodifiable map of target allocations keyed by uppercase ticker symbol
     */
    public Map<String, Allocation> getAllocations() {
        return Collections.unmodifiableMap(allocations);
    }

    /**
     * Clears all target allocations from this portfolio.
     */
    public void clearAllocations() {
        allocations.clear();
    }

    /**
     * Indicates whether any target allocations are currently configured.
     *
     * @return {@code true} if at least one allocation is defined, {@code false} otherwise
     */
    public boolean hasAllocations() {
        return !allocations.isEmpty();
    }

    /**
     * Validates that the current target allocations defined on this portfolio sum to exactly 100% ({@code 1.00}).
     *
     * @throws IllegalStateException if no allocations are defined, or if the sum does not equal 1.00
     */
    public void validateAllocations() {
        if (allocations.isEmpty()) {
            throw new IllegalStateException("Cannot rebalance: no target allocations defined");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Allocation allocation : allocations.values()) {
            sum = sum.add(allocation.targetPercentage());
        }
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException("Total target allocation must equal 100% (1.00), got: " + sum);
        }
    }

    /**
     * Checks whether the current target allocations are valid and sum to 100% ({@code 1.00}).
     *
     * @return {@code true} if allocations are non-empty and sum to 1.00, {@code false} otherwise
     */
    public boolean isAllocationsValid() {
        if (allocations.isEmpty()) {
            return false;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Allocation allocation : allocations.values()) {
            sum = sum.add(allocation.targetPercentage());
        }
        return sum.compareTo(BigDecimal.ONE) == 0;
    }

    // =========================================================================
    // Portfolio Valuation & Rebalancing
    // =========================================================================

    /**
     * Calculates the total market value of the portfolio.
     * <p>
     * Sums the current market values of all held positions ({@link PortfolioPosition#getMarketValue()}).
     * If the portfolio has no positions, returns {@code 0.00} with scale 2 using banker's rounding.
     * </p>
     *
     * @return total market valuation formatted to 2 decimal places with {@link RoundingMode#HALF_EVEN}
     * @throws IllegalStateException if any held stock lacks an established market price quote
     */
    public BigDecimal getTotalValue() {
        if (positions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition position : positions.values()) {
            total = total.add(position.getMarketValue());
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Executes the rebalancing algorithm to determine which stocks should be sold and which ones
     * should be bought to align the portfolio holdings with the target distribution.
     * <p>
     * Enforces the self-financing execution sequence (SELL orders before BUY orders) guaranteeing
     * that sale proceeds fund subsequent acquisitions.
     * </p>
     *
     * @return an immutable {@link RebalancePlan} containing ordered trades and valuation metadata
     * @throws IllegalStateException if no target allocations are defined, target allocations do not sum to 100%,
     *                                or any stock in positions or allocations lacks an established price
     */
    public RebalancePlan rebalance() {
        if (allocations.isEmpty()) {
            throw new IllegalStateException("Cannot rebalance: no target allocations defined");
        }
        validateAllocations();
        return rebalanceEngine.calculateRebalance(positions, allocations, getTotalValue());
    }

    /**
     * Returns the underlying rebalancing engine configured on this portfolio.
     *
     * @return the {@link RebalanceEngine} instance
     */
    public RebalanceEngine getRebalanceEngine() {
        return rebalanceEngine;
    }
}
