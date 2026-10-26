package com.trading.portfolio.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable record representing an individual trade order generated during portfolio rebalancing.
 * <p>
 * Contains the target {@link Stock}, the trade operation type ({@link OrderAction#BUY} or
 * {@link OrderAction#SELL}), the quantity of shares to be traded (supporting fractional shares),
 * and the estimated monetary value of the transaction calculated using the stock's current market price.
 * </p>
 *
 * @param stock the target stock to trade, must not be null
 * @param action the action to perform (BUY or SELL), must not be null
 * @param sharesToTrade number of shares to trade, must be strictly positive (&gt; 0) and not null
 * @param estimatedAmount estimated monetary transaction value, must be strictly positive (&gt; 0) and not null
 */
public record RebalanceOrder(
        Stock stock,
        OrderAction action,
        BigDecimal sharesToTrade,
        BigDecimal estimatedAmount
) {

    /**
     * Compact constructor validating constraints on order attributes.
     *
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if {@code sharesToTrade} or {@code estimatedAmount} is non-positive (&le; 0)
     */
    public RebalanceOrder {
        Objects.requireNonNull(stock, "Stock cannot be null");
        Objects.requireNonNull(action, "Order action cannot be null");
        Objects.requireNonNull(sharesToTrade, "Shares to trade cannot be null");
        Objects.requireNonNull(estimatedAmount, "Estimated amount cannot be null");

        if (sharesToTrade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Shares to trade must be strictly positive (> 0), got: " + sharesToTrade);
        }
        if (estimatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Estimated amount must be strictly positive (> 0), got: " + estimatedAmount);
        }
    }

    /**
     * Convenience constructor accepting primitive double values.
     *
     * @param stock the target stock to trade, must not be null
     * @param action the action to perform (BUY or SELL), must not be null
     * @param sharesToTrade number of shares to trade as double (&gt; 0)
     * @param estimatedAmount estimated amount as double (&gt; 0)
     */
    public RebalanceOrder(Stock stock, OrderAction action, double sharesToTrade, double estimatedAmount) {
        this(stock, action, BigDecimal.valueOf(sharesToTrade), BigDecimal.valueOf(estimatedAmount));
    }

    /**
     * Checks if this order is a purchase order ({@link OrderAction#BUY}).
     *
     * @return {@code true} if action is BUY, {@code false} otherwise
     */
    public boolean isBuy() {
        return action == OrderAction.BUY;
    }

    /**
     * Checks if this order is a sale order ({@link OrderAction#SELL}).
     *
     * @return {@code true} if action is SELL, {@code false} otherwise
     */
    public boolean isSell() {
        return action == OrderAction.SELL;
    }

    /**
     * Returns a human-readable string representation of this rebalance order.
     * <p>
     * Formatted as: {@code "[ACTION] [shares] [SYMBOL] (~$[amount])"}, for example:
     * {@code "SELL 2.0000 META (~$200.00)"}.
     * </p>
     *
     * @return formatted string representation of this order
     */
    @Override
    public String toString() {
        String formattedShares = sharesToTrade.scale() < 4
                ? sharesToTrade.setScale(4, RoundingMode.HALF_EVEN).toPlainString()
                : sharesToTrade.toPlainString();
        String formattedAmount = estimatedAmount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
        return String.format(Locale.US, "%s %s %s (~$%s)",
                action,
                formattedShares,
                stock.getSymbol(),
                formattedAmount);
    }
}
