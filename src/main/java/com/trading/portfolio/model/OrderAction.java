package com.trading.portfolio.model;

/**
 * Represents the action to be taken in a portfolio rebalance order.
 * <p>
 * During portfolio rebalancing, target allocations are compared against
 * current allocations to generate orders with either {@link #BUY} or {@link #SELL} actions.
 * </p>
 */
public enum OrderAction {
    /**
     * Buy shares of a stock to reach target allocation or invest available cash.
     */
    BUY,

    /**
     * Sell shares of a stock to decrease allocation or reallocate capital.
     */
    SELL
}
