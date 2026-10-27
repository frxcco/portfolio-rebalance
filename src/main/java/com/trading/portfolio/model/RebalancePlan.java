package com.trading.portfolio.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 * Precomputes segregated SELL and BUY order pipelines and cumulative monetary amounts
 * in a single pass during construction, providing O(1) query performance and
 * clean JSON serialization properties.
 * </p>
 *
 * @param orders immutable list of all individual rebalancing orders, must not be null
 * @param sellOrders immutable list of pre-filtered SELL orders, must not be null
 * @param buyOrders immutable list of pre-filtered BUY orders, must not be null
 * @param totalSellAmount precomputed total proceeds from SELL orders, must not be null
 * @param totalBuyAmount precomputed total capital required for BUY orders, must not be null
 * @param totalPortfolioValueBefore total value of the portfolio prior to execution, must not be null and non-negative
 */
public record RebalancePlan(
        List<RebalanceOrder> orders,
        List<RebalanceOrder> sellOrders,
        List<RebalanceOrder> buyOrders,
        BigDecimal totalSellAmount,
        BigDecimal totalBuyAmount,
        BigDecimal totalPortfolioValueBefore
) {

    /**
     * Compact constructor validating constraints and enforcing immutable defensive copies.
     *
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if {@code totalPortfolioValueBefore} is negative (&lt; 0)
     */
    public RebalancePlan {
        Objects.requireNonNull(orders, "Orders list cannot be null");
        Objects.requireNonNull(sellOrders, "Sell orders list cannot be null");
        Objects.requireNonNull(buyOrders, "Buy orders list cannot be null");
        Objects.requireNonNull(totalSellAmount, "Total sell amount cannot be null");
        Objects.requireNonNull(totalBuyAmount, "Total buy amount cannot be null");
        Objects.requireNonNull(totalPortfolioValueBefore, "Total portfolio value before cannot be null");

        if (totalPortfolioValueBefore.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Total portfolio value before cannot be negative, got: " + totalPortfolioValueBefore);
        }
        orders = List.copyOf(orders);
        sellOrders = List.copyOf(sellOrders);
        buyOrders = List.copyOf(buyOrders);
    }

    /**
     * Standard constructor taking the full orders list and precomputing segregated collections
     * and monetary totals during instantiation.
     *
     * @param orders list of rebalance orders, must not be null
     * @param totalPortfolioValueBefore total portfolio value before execution, must not be null and non-negative
     */
    public RebalancePlan(List<RebalanceOrder> orders, BigDecimal totalPortfolioValueBefore) {
        this(
                Objects.requireNonNull(orders, "Orders list cannot be null"),
                filterOrdersByAction(orders, OrderAction.SELL),
                filterOrdersByAction(orders, OrderAction.BUY),
                sumOrderAmounts(orders, OrderAction.SELL),
                sumOrderAmounts(orders, OrderAction.BUY),
                totalPortfolioValueBefore
        );
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
        Objects.requireNonNull(totalPortfolioValueBefore, "Total portfolio value before cannot be null");
        return new RebalancePlan(
                List.of(),
                List.of(),
                List.of(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                totalPortfolioValueBefore
        );
    }

    private static List<RebalanceOrder> filterOrdersByAction(List<RebalanceOrder> orders, OrderAction action) {
        List<RebalanceOrder> filtered = new ArrayList<>();
        for (RebalanceOrder order : orders) {
            if (order != null && order.action() == action) {
                filtered.add(order);
            }
        }
        return filtered;
    }

    private static BigDecimal sumOrderAmounts(List<RebalanceOrder> orders, OrderAction action) {
        BigDecimal sum = BigDecimal.ZERO;
        for (RebalanceOrder order : orders) {
            if (order != null && order.action() == action) {
                sum = sum.add(order.estimatedAmount());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Returns the pre-filtered, unmodifiable list containing exclusively the SELL orders in this plan.
     *
     * @return unmodifiable list of SELL {@link RebalanceOrder} instances (O(1) access)
     */
    public List<RebalanceOrder> getSellOrders() {
        return sellOrders;
    }

    /**
     * Returns the pre-filtered, unmodifiable list containing exclusively the BUY orders in this plan.
     *
     * @return unmodifiable list of BUY {@link RebalanceOrder} instances (O(1) access)
     */
    public List<RebalanceOrder> getBuyOrders() {
        return buyOrders;
    }

    /**
     * Returns the precomputed sum of estimated proceeds across all SELL orders.
     *
     * @return total estimated proceeds from SELL orders (O(1) access)
     */
    public BigDecimal getTotalSellAmount() {
        return totalSellAmount;
    }

    /**
     * Returns the precomputed sum of estimated capital required across all BUY orders.
     *
     * @return total estimated capital for BUY orders (O(1) access)
     */
    public BigDecimal getTotalBuyAmount() {
        return totalBuyAmount;
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
     * @return total order count (O(1) access)
     */
    public int getOrderCount() {
        return orders.size();
    }

    /**
     * Alias getter for standard JavaBean compatibility returning the orders list.
     *
     * @return immutable list of orders
     */
    public List<RebalanceOrder> getOrders() {
        return orders;
    }

    /**
     * Alias getter for standard JavaBean compatibility returning the total portfolio value before rebalancing.
     *
     * @return total portfolio value before
     */
    public BigDecimal getTotalPortfolioValueBefore() {
        return totalPortfolioValueBefore;
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
                totalSellAmount.toPlainString(),
                totalBuyAmount.toPlainString(),
                totalPortfolioValueBefore.setScale(2, RoundingMode.HALF_EVEN).toPlainString());
    }
}
