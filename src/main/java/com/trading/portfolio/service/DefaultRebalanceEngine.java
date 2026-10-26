package com.trading.portfolio.service;

import com.trading.portfolio.model.Allocation;
import com.trading.portfolio.model.OrderAction;
import com.trading.portfolio.model.PortfolioPosition;
import com.trading.portfolio.model.RebalanceOrder;
import com.trading.portfolio.model.RebalancePlan;
import com.trading.portfolio.model.Stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Standard implementation of {@link RebalanceEngine} providing precise financial calculation,
 * deterministic ordering, and self-financing trade sequence guarantees.
 * <p>
 * Core mathematical and algorithmic properties:
 * <ul>
 *   <li><b>Precision Scales:</b> Monetary amounts use scale 2, share quantities use scale 4,
 *       and intermediate values use scale 8.</li>
 *   <li><b>Rounding:</b> Uses {@link RoundingMode#HALF_EVEN} (Banker's rounding) to minimize statistical bias.</li>
 *   <li><b>Threshold Tolerance:</b> Disregards monetary deltas falling within {@code [-threshold, threshold]}
 *       (default $0.005) to prevent executing sub-penny trades due to rounding artifacts.</li>
 *   <li><b>Asset Liquidation:</b> Discontinued assets (target allocation = 0%) are liquidated in their entirety
 *       without fractional dust remnants.</li>
 *   <li><b>Self-Financing Priority:</b> All {@link OrderAction#SELL} orders precede {@link OrderAction#BUY}
 *       orders to fund purchases from sale proceeds.</li>
 * </ul>
 * </p>
 */
public class DefaultRebalanceEngine implements RebalanceEngine {

    /**
     * Scale for monetary currency values (2 decimal places).
     */
    public static final int MONEY_SCALE = 2;

    /**
     * Scale for share quantities (4 decimal places for fractional share precision).
     */
    public static final int SHARES_SCALE = 4;

    /**
     * Intermediate precision scale for target values and monetary calculations (8 decimal places).
     */
    public static final int CALCULATION_SCALE = 8;

    /**
     * Standard rounding mode across financial calculations: Banker's rounding ({@link RoundingMode#HALF_EVEN}).
     */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    /**
     * Default monetary delta tolerance threshold ($0.005) under which trades are omitted.
     */
    public static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(0.005);

    /**
     * Configured monetary difference tolerance threshold.
     */
    private final BigDecimal threshold;

    /**
     * Constructs a {@code DefaultRebalanceEngine} with the default monetary tolerance threshold ($0.005).
     */
    public DefaultRebalanceEngine() {
        this(DEFAULT_THRESHOLD);
    }

    /**
     * Constructs a {@code DefaultRebalanceEngine} with a custom monetary tolerance threshold.
     *
     * @param threshold minimum monetary difference required to generate a rebalance order, must not be null or negative
     * @throws NullPointerException if {@code threshold} is null
     * @throws IllegalArgumentException if {@code threshold} is negative (&lt; 0)
     */
    public DefaultRebalanceEngine(BigDecimal threshold) {
        Objects.requireNonNull(threshold, "Threshold cannot be null");
        if (threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative, got: " + threshold);
        }
        this.threshold = threshold;
    }

    /**
     * Returns the monetary difference tolerance threshold configured on this engine.
     *
     * @return threshold as {@link BigDecimal}
     */
    public BigDecimal getThreshold() {
        return threshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RebalancePlan calculateRebalance(
            Map<String, PortfolioPosition> currentPositions,
            Map<String, Allocation> targetAllocations,
            BigDecimal totalPortfolioValue
    ) {
        // 1. Prior validations
        Objects.requireNonNull(currentPositions, "Current positions map cannot be null");
        Objects.requireNonNull(targetAllocations, "Target allocations map cannot be null");
        Objects.requireNonNull(totalPortfolioValue, "Total portfolio value cannot be null");

        // Verify that all stocks have established prices (> 0)
        for (Map.Entry<String, PortfolioPosition> entry : currentPositions.entrySet()) {
            PortfolioPosition position = entry.getValue();
            String symbol = entry.getKey();
            if (position == null || position.stock() == null || !position.stock().hasPrice()
                    || position.stock().getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Cannot rebalance: price not set for stock " + symbol);
            }
        }

        for (Map.Entry<String, Allocation> entry : targetAllocations.entrySet()) {
            Allocation allocation = entry.getValue();
            String symbol = entry.getKey();
            if (allocation == null || allocation.stock() == null || !allocation.stock().hasPrice()
                    || allocation.stock().getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Cannot rebalance: price not set for stock " + symbol);
            }
        }

        // 2. Base case: zero or negative portfolio value
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RebalancePlan.empty(totalPortfolioValue);
        }

        // 3. Asset universe: deterministic union of all tickers in natural alphabetical order
        Set<String> allSymbols = new TreeSet<>(currentPositions.keySet());
        allSymbols.addAll(targetAllocations.keySet());

        List<RebalanceOrder> sellOrders = new ArrayList<>();
        List<RebalanceOrder> buyOrders = new ArrayList<>();

        // 4. Delta calculations per asset
        for (String symbol : allSymbols) {
            PortfolioPosition position = currentPositions.get(symbol);
            Allocation allocation = targetAllocations.get(symbol);

            Stock stock = (position != null) ? position.stock() : allocation.stock();
            BigDecimal price = stock.getCurrentPrice();

            BigDecimal currentShares = (position != null) ? position.shares() : BigDecimal.ZERO;
            BigDecimal currentValue = currentShares.multiply(price);

            BigDecimal targetPercentage = (allocation != null) ? allocation.targetPercentage() : BigDecimal.ZERO;
            BigDecimal targetValue = totalPortfolioValue.multiply(targetPercentage).setScale(CALCULATION_SCALE, ROUNDING_MODE);

            BigDecimal deltaValue = targetValue.subtract(currentValue);

            // a) Discontinued asset: target percentage is 0 and current holdings > 0
            if (targetPercentage.compareTo(BigDecimal.ZERO) == 0 && currentShares.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal sharesToSell = currentShares;
                BigDecimal estimatedAmount = sharesToSell.multiply(price).setScale(MONEY_SCALE, ROUNDING_MODE);
                if (sharesToSell.compareTo(BigDecimal.ZERO) > 0 && estimatedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    sellOrders.add(new RebalanceOrder(stock, OrderAction.SELL, sharesToSell, estimatedAmount));
                }
            }
            // b) Overweighted: deltaValue < -threshold -> SELL
            else if (deltaValue.compareTo(threshold.negate()) < 0) {
                BigDecimal sharesToSell = deltaValue.abs().divide(price, SHARES_SCALE, ROUNDING_MODE);
                if (sharesToSell.compareTo(currentShares) > 0) {
                    sharesToSell = currentShares;
                }
                BigDecimal estimatedAmount = sharesToSell.multiply(price).setScale(MONEY_SCALE, ROUNDING_MODE);
                if (sharesToSell.compareTo(BigDecimal.ZERO) > 0 && estimatedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    sellOrders.add(new RebalanceOrder(stock, OrderAction.SELL, sharesToSell, estimatedAmount));
                }
            }
            // c) Underweighted or new asset: deltaValue > threshold -> BUY
            else if (deltaValue.compareTo(threshold) > 0) {
                BigDecimal sharesToBuy = deltaValue.divide(price, SHARES_SCALE, ROUNDING_MODE);
                BigDecimal estimatedAmount = sharesToBuy.multiply(price).setScale(MONEY_SCALE, ROUNDING_MODE);
                if (sharesToBuy.compareTo(BigDecimal.ZERO) > 0 && estimatedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    buyOrders.add(new RebalanceOrder(stock, OrderAction.BUY, sharesToBuy, estimatedAmount));
                }
            }
            // d) Within balance threshold ([-threshold, threshold]): no order generated
        }

        // 5. Liquidity Priority Order: all SELL orders first, followed by all BUY orders
        List<RebalanceOrder> orders = new ArrayList<>(sellOrders.size() + buyOrders.size());
        orders.addAll(sellOrders);
        orders.addAll(buyOrders);

        // 6. Return populated rebalance execution plan
        return new RebalancePlan(orders, totalPortfolioValue);
    }
}
