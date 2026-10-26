package com.trading.portfolio.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable domain representation of a complete portfolio rebalance execution plan.
 * <p>
 * Aggregates all necessary {@link RebalanceOrder} trade operations (both BUY and SELL)
 * along with baseline context such as the portfolio's total valuation prior to rebalancing.
 * </p>
 * <p>
 * Provides business query methods to retrieve separate SELL or BUY order pipelines,
 * calculate cumulative monetary amounts, check if rebalancing actions are required,
 * and assess overall order statistics.
 * </p>
 *
 * @param orders immutable list of individual rebalancing orders, must not be null
 * @param totalPortfolioValueBefore total value of the portfolio prior to execution, must not be null and non-negative
 */
public record RebalancePlan(
        List<RebalanceOrder> orders,
        BigDecimal totalPortfolioValueBefore
) {

    /**
     * Compact constructor creating an immutable copy of the orders list and validating inputs.
     *
     * @throws NullPointerException if {@code orders} or {@code totalPortfolioValueBefore} is null
     * @throws IllegalArgumentException if {@code totalPortfolioValueBefore} is negative (&lt; 0)
     */
    public RebalancePlan {
        Objects.requireNonNull(orders, "Orders list cannot be null");
        Objects.requireNonNull(totalPortfolioValueBefore, "Total portfolio value before cannot be null");
        if (totalPortfolioValueBefore.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Total portfolio value before cannot be negative, got: " + totalPortfolioValueBefore);
        }
        orders = List.copyOf(orders);
    }

    /**
     * Convenience constructor accepting portfolio value as a {@code double}.
     *
     * @param orders list of rebalance orders, must not be null
     * @param totalPortfolioValueBefore total portfolio value before as a double (&ge; 0)
     */
    public RebalancePlan(List<RebalanceOrder> orders, double totalPortfolioValueBefore) {
        this(orders, BigDecimal.valueOf(totalPortfolioValueBefore));
    }

    /**
     * Factory method creating an empty rebalance plan when the portfolio is already balanced.
     *
     * @param totalPortfolioValueBefore current total portfolio valuation, must not be null
     * @return a {@link RebalancePlan} with no orders
     */
    public static RebalancePlan empty(BigDecimal totalPortfolioValueBefore) {
        return new RebalancePlan(List.of(), totalPortfolioValueBefore);
    }

    /**
     * Returns an unmodifiable list containing exclusively the SELL orders in this plan.
     *
     * @return unmodifiable list of SELL {@link RebalanceOrder} instances
     */
    public List<RebalanceOrder> getSellOrders() {
        return orders.stream()
                .filter(RebalanceOrder::isSell)
                .toList();
    }

    /**
     * Returns an unmodifiable list containing exclusively the BUY orders in this plan.
     *
     * @return unmodifiable list of BUY {@link RebalanceOrder} instances
     */
    public List<RebalanceOrder> getBuyOrders() {
        return orders.stream()
                .filter(RebalanceOrder::isBuy)
                .toList();
    }

    /**
     * Checks if this plan requires no trade operations (e.g., when the portfolio is already balanced).
     *
     * @return {@code true} if there are no orders to execute, {@code false} otherwise
     */
    public boolean isEmpty() {
        return orders.isEmpty();
    }

    /**
     * Returns the total count of trade orders in this plan.
     *
     * @return total order count
     */
    public int getOrderCount() {
        return orders.size();
    }

    /**
     * Calculates the sum of estimated amounts across all SELL orders in this plan.
     *
     * @return total estimated proceeds from SELL orders, rounded to 2 decimal places
     */
    public BigDecimal getTotalSellAmount() {
        return orders.stream()
                .filter(RebalanceOrder::isSell)
                .map(RebalanceOrder::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Calculates the sum of estimated amounts across all BUY orders in this plan.
     *
     * @return total estimated capital required for BUY orders, rounded to 2 decimal places
     */
    public BigDecimal getTotalBuyAmount() {
        return orders.stream()
                .filter(RebalanceOrder::isBuy)
                .map(RebalanceOrder::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Alias getter for standard JavaBean compatibility returning the orders list.
     *
     * @return immutable list of orders
     */
    public List<RebalanceOrder> getOrders() {
        return orders();
    }

    /**
     * Alias getter for standard JavaBean compatibility returning the total portfolio value before rebalancing.
     *
     * @return total portfolio value before
     */
    public BigDecimal getTotalPortfolioValueBefore() {
        return totalPortfolioValueBefore();
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return String.format(Locale.US,
                    "RebalancePlan[orders=0, status=BALANCED, totalPortfolioValueBefore=$%s]",
                    totalPortfolioValueBefore.setScale(2, RoundingMode.HALF_EVEN).toPlainString());
        }
        return String.format(Locale.US,
                "RebalancePlan[orders=%d, totalSell=$%s, totalBuy=$%s, totalPortfolioValueBefore=$%s]",
                orders.size(),
                getTotalSellAmount().toPlainString(),
                getTotalBuyAmount().toPlainString(),
                totalPortfolioValueBefore.setScale(2, RoundingMode.HALF_EVEN).toPlainString());
    }
}
