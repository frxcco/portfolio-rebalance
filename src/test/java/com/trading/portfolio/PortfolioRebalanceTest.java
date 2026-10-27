package com.trading.portfolio;

import com.trading.portfolio.model.Allocation;
import com.trading.portfolio.model.OrderAction;
import com.trading.portfolio.model.PortfolioPosition;
import com.trading.portfolio.model.RebalanceOrder;
import com.trading.portfolio.model.RebalancePlan;
import com.trading.portfolio.model.Stock;
import com.trading.portfolio.service.DefaultRebalanceEngine;
import com.trading.portfolio.service.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive Unit and Integration Test Suite for Portfolio Rebalancing.
 * <p>
 * Validates domain rules, financial arithmetic, liquidity ordering,
 * and edge cases mapped to the Software Design Document (SDD).
 * </p>
 */
@DisplayName("Portfolio Rebalance Engine & Domain Test Suite")
class PortfolioRebalanceTest {

    private Stock meta;
    private Stock aapl;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        meta = new Stock("META", "Meta Platforms Inc.", new BigDecimal("100.00"));
        aapl = new Stock("AAPL", "Apple Inc.", new BigDecimal("100.00"));
        portfolio = new Portfolio();
    }

    // =========================================================================
    // TC_01: Base Scenario Rebalance (Sell META and Buy AAPL)
    // =========================================================================

    @Test
    @DisplayName("TC_01: Base scenario rebalances portfolio by selling META and buying AAPL")
    void TC_01_base_scenario_rebalance_sell_meta_and_buy_aapl() {
        // Given: Portfolio with 10 META @ $100 ($1,000) and 10 AAPL @ $100 ($1,000). Total: $2,000.
        portfolio.addPosition(meta, new BigDecimal("10"));
        portfolio.addPosition(aapl, new BigDecimal("10"));

        // Target allocation: 40% META ($800) and 60% AAPL ($1,200).
        portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.40")),
                new Allocation(aapl, new BigDecimal("0.60"))
        );

        // When: Rebalancing the portfolio
        RebalancePlan plan = portfolio.rebalance();

        // Then:
        // 1. Total portfolio value before rebalance is $2,000.00
        assertThat(plan.totalPortfolioValueBefore()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(plan.isEmpty()).isFalse();
        assertThat(plan.getOrderCount()).isEqualTo(2);

        // 2. Exactly 1 SELL order and 1 BUY order generated
        List<RebalanceOrder> sellOrders = plan.getSellOrders();
        List<RebalanceOrder> buyOrders = plan.getBuyOrders();
        assertThat(sellOrders).hasSize(1);
        assertThat(buyOrders).hasSize(1);

        // 3. Verify SELL order: 2.0000 META (~$200.00)
        RebalanceOrder sellMeta = sellOrders.get(0);
        assertThat(sellMeta.stock()).isEqualTo(meta);
        assertThat(sellMeta.action()).isEqualTo(OrderAction.SELL);
        assertThat(sellMeta.isSell()).isTrue();
        assertThat(sellMeta.sharesToTrade()).isEqualByComparingTo(new BigDecimal("2.0000"));
        assertThat(sellMeta.estimatedAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(sellMeta.toString()).isEqualTo("SELL 2.0000 META (~$200.00)");

        // 4. Verify BUY order: 2.0000 AAPL (~$200.00)
        RebalanceOrder buyAapl = buyOrders.get(0);
        assertThat(buyAapl.stock()).isEqualTo(aapl);
        assertThat(buyAapl.action()).isEqualTo(OrderAction.BUY);
        assertThat(buyAapl.isBuy()).isTrue();
        assertThat(buyAapl.sharesToTrade()).isEqualByComparingTo(new BigDecimal("2.0000"));
        assertThat(buyAapl.estimatedAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(buyAapl.toString()).isEqualTo("BUY 2.0000 AAPL (~$200.00)");

        // 5. Total sell proceeds exactly match total capital required for purchases ($200.00)
        assertThat(plan.getTotalSellAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(plan.getTotalBuyAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(plan.getTotalSellAmount()).isEqualByComparingTo(plan.getTotalBuyAmount());
    }

    // =========================================================================
    // TC_02: Already Balanced Portfolio Returns Empty Plan
    // =========================================================================

    @Test
    @DisplayName("TC_02: Already balanced portfolio returns empty execution plan")
    void TC_02_already_balanced_portfolio_returns_empty_plan() {
        // Given: Portfolio already aligned with target weights (40% META = $400, 60% AAPL = $600, total = $1,000)
        portfolio.addPosition(meta, new BigDecimal("4"));
        portfolio.addPosition(aapl, new BigDecimal("6"));

        portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.40")),
                new Allocation(aapl, new BigDecimal("0.60"))
        );

        // When: Rebalancing the portfolio
        RebalancePlan plan = portfolio.rebalance();

        // Then:
        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.getOrderCount()).isZero();
        assertThat(plan.orders()).isEmpty();
        assertThat(plan.getSellOrders()).isEmpty();
        assertThat(plan.getBuyOrders()).isEmpty();
        assertThat(plan.getTotalSellAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.getTotalBuyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.totalPortfolioValueBefore()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // =========================================================================
    // TC_03: Unowned Asset in Target Generates Buy Order
    // =========================================================================

    @Test
    @DisplayName("TC_03: Target asset not currently owned generates a BUY order")
    void TC_03_unowned_asset_in_target_generates_buy_order() {
        // Given: Portfolio holding only META (10 shares @ $100 = $1,000)
        Stock googl = new Stock("GOOGL", "Alphabet Inc.", new BigDecimal("150.00"));
        portfolio.addPosition(meta, new BigDecimal("10"));

        // Target: 70% META ($700) and 30% GOOGL ($300, unowned asset)
        portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.70")),
                new Allocation(googl, new BigDecimal("0.30"))
        );

        // When: Rebalancing the portfolio
        RebalancePlan plan = portfolio.rebalance();

        // Then:
        assertThat(plan.isEmpty()).isFalse();
        assertThat(plan.getOrderCount()).isEqualTo(2);

        // SELL 3.0000 META to release $300.00
        List<RebalanceOrder> sellOrders = plan.getSellOrders();
        assertThat(sellOrders).hasSize(1);
        assertThat(sellOrders.get(0).stock()).isEqualTo(meta);
        assertThat(sellOrders.get(0).sharesToTrade()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(sellOrders.get(0).estimatedAmount()).isEqualByComparingTo(new BigDecimal("300.00"));

        // BUY GOOGL with 30% of portfolio ($300 / $150 = 2.0000 shares)
        List<RebalanceOrder> buyOrders = plan.getBuyOrders();
        assertThat(buyOrders).hasSize(1);
        RebalanceOrder buyGoogl = buyOrders.get(0);
        assertThat(buyGoogl.stock()).isEqualTo(googl);
        assertThat(buyGoogl.action()).isEqualTo(OrderAction.BUY);
        assertThat(buyGoogl.sharesToTrade()).isEqualByComparingTo(new BigDecimal("2.0000"));
        assertThat(buyGoogl.estimatedAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    // =========================================================================
    // TC_04: Discontinued Asset Liquidates Entire Position
    // =========================================================================

    @Test
    @DisplayName("TC_04: Discontinued asset liquidates entire position without remnants")
    void TC_04_discontinued_asset_liquidates_entire_position() {
        // Given: Portfolio holding META (5 shares @ $100 = $500) and NFLX (2.5 shares @ $50 = $125). Total: $625.
        Stock nflx = new Stock("NFLX", "Netflix Inc.", new BigDecimal("50.00"));
        portfolio.addPosition(meta, new BigDecimal("5"));
        portfolio.addPosition(nflx, new BigDecimal("2.5"));

        // Target allocation: 100% META, NFLX is omitted / 0%
        portfolio.setAllocations(new Allocation(meta, BigDecimal.ONE));

        // When: Rebalancing the portfolio
        RebalancePlan plan = portfolio.rebalance();

        // Then:
        assertThat(plan.isEmpty()).isFalse();

        // NFLX must be completely liquidated: exactly 2.5000 shares sold for $125.00
        List<RebalanceOrder> sellOrders = plan.getSellOrders();
        assertThat(sellOrders).hasSize(1);
        RebalanceOrder sellNflx = sellOrders.get(0);
        assertThat(sellNflx.stock()).isEqualTo(nflx);
        assertThat(sellNflx.action()).isEqualTo(OrderAction.SELL);
        assertThat(sellNflx.sharesToTrade()).isEqualByComparingTo(new BigDecimal("2.5000"));
        assertThat(sellNflx.estimatedAmount()).isEqualByComparingTo(new BigDecimal("125.00"));

        // Proceeds ($125.00) fund buying META (125 / 100 = 1.2500 shares)
        List<RebalanceOrder> buyOrders = plan.getBuyOrders();
        assertThat(buyOrders).hasSize(1);
        RebalanceOrder buyMeta = buyOrders.get(0);
        assertThat(buyMeta.stock()).isEqualTo(meta);
        assertThat(buyMeta.action()).isEqualTo(OrderAction.BUY);
        assertThat(buyMeta.sharesToTrade()).isEqualByComparingTo(new BigDecimal("1.2500"));
        assertThat(buyMeta.estimatedAmount()).isEqualByComparingTo(new BigDecimal("125.00"));

        // Total sell amount equals total buy amount
        assertThat(plan.getTotalSellAmount()).isEqualByComparingTo(plan.getTotalBuyAmount());
    }

    // =========================================================================
    // TC_05: Invalid Allocations Throw Exception
    // =========================================================================

    @Test
    @DisplayName("TC_05: Invalid allocations throw IllegalArgumentException")
    void TC_05_invalid_allocations_throw_exception() {
        // 1. Total allocation sums to 90% (0.90)
        assertThatThrownBy(() -> portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.50")),
                new Allocation(aapl, new BigDecimal("0.40"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total target allocation must equal 100% (1.00)");

        // 2. Total allocation sums to 110% (1.10)
        assertThatThrownBy(() -> portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.60")),
                new Allocation(aapl, new BigDecimal("0.50"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total target allocation must equal 100% (1.00)");

        // 3. Individual allocation is negative
        assertThatThrownBy(() -> new Allocation(meta, new BigDecimal("-0.10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target percentage must be between 0.0 and 1.0");

        // 4. Individual allocation exceeds 1.00 (100%)
        assertThatThrownBy(() -> new Allocation(meta, new BigDecimal("1.05")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target percentage must be between 0.0 and 1.0");

        // 5. Duplicate ticker allocations in setAllocations
        assertThatThrownBy(() -> portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.50")),
                new Allocation(new Stock("META", "Meta Duplicate"), new BigDecimal("0.50"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate allocation for stock symbol: META");
    }

    // =========================================================================
    // TC_06: Missing or Invalid Price Throws Exception
    // =========================================================================

    @Test
    @DisplayName("TC_06: Missing or invalid stock price throws expected exceptions")
    void TC_06_missing_or_invalid_price_throws_exception() {
        // Given: Stock without an initial market price
        Stock unpricedStock = new Stock("MSFT", "Microsoft Corp.");
        assertThat(unpricedStock.hasPrice()).isFalse();

        // 1. Calling getCurrentPrice() on an unpriced stock throws IllegalStateException
        assertThatThrownBy(unpricedStock::getCurrentPrice)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Price has not been set for stock: MSFT");

        // 2. Attempting to set price <= 0 throws IllegalArgumentException
        assertThatThrownBy(() -> unpricedStock.updateCurrentPrice(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be strictly positive");

        assertThatThrownBy(() -> unpricedStock.updateCurrentPrice(new BigDecimal("-15.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be strictly positive");

        assertThatThrownBy(() -> unpricedStock.updateCurrentPrice(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price cannot be null");

        // 3. Constructor with price <= 0 throws IllegalArgumentException
        assertThatThrownBy(() -> new Stock("NVDA", "Nvidia Corp.", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be strictly positive");

        assertThatThrownBy(() -> new Stock("NVDA", "Nvidia Corp.", new BigDecimal("-10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be strictly positive");

        // 4. Attempting to rebalance a portfolio containing a stock without price throws IllegalStateException
        portfolio.addPosition(unpricedStock, new BigDecimal("5"));
        portfolio.setAllocations(new Allocation(unpricedStock, BigDecimal.ONE));

        assertThatThrownBy(() -> portfolio.rebalance())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MSFT");

        // Also verify when unpriced stock is only in target allocation (and not in positions)
        Portfolio portfolioWithTargetUnpriced = new Portfolio();
        portfolioWithTargetUnpriced.addPosition(meta, 10);
        portfolioWithTargetUnpriced.setAllocations(
                new Allocation(meta, new BigDecimal("0.50")),
                new Allocation(unpricedStock, new BigDecimal("0.50"))
        );
        assertThatThrownBy(portfolioWithTargetUnpriced::rebalance)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot rebalance: price not set for stock MSFT");
    }

    // =========================================================================
    // TC_07: HALF_EVEN Rounding Precision Preserves Cents
    // =========================================================================

    @Test
    @DisplayName("TC_07: Banker's rounding (HALF_EVEN) preserves exact cents in fractional distributions")
    void TC_07_half_even_rounding_precision_preserves_cents() {
        // Given: Three stocks with distinct prices and non-integer target weights
        // Target: 33.33% STKA, 33.33% STKB, 33.34% STKC (sums to exactly 100.00%)
        Stock stockA = new Stock("STKA", "Asset A", new BigDecimal("30.00"));
        Stock stockB = new Stock("STKB", "Asset B", new BigDecimal("45.00"));
        Stock stockC = new Stock("STKC", "Asset C", new BigDecimal("60.00"));

        // Portfolio initially holds 100 shares of STKA @ $30.00 = $3,000.00
        portfolio.addPosition(stockA, new BigDecimal("100.0000"));

        portfolio.setAllocations(
                new Allocation(stockA, new BigDecimal("0.3333")),
                new Allocation(stockB, new BigDecimal("0.3333")),
                new Allocation(stockC, new BigDecimal("0.3334"))
        );

        // When: Rebalancing the portfolio
        RebalancePlan plan = portfolio.rebalance();

        // Then:
        // Expected valuations:
        // Total value = $3,000.00
        // Target STKA = 3,000 * 0.3333 = $999.90 -> Delta = -$2,000.10 (SELL 66.6700 shares)
        // Target STKB = 3,000 * 0.3333 = $999.90 -> Delta = +$999.90 (BUY 22.2200 shares)
        // Target STKC = 3,000 * 0.3334 = $1,000.20 -> Delta = +$1,000.20 (BUY 16.6700 shares)
        assertThat(plan.totalPortfolioValueBefore()).isEqualByComparingTo(new BigDecimal("3000.00"));

        BigDecimal totalSell = plan.getTotalSellAmount();
        BigDecimal totalBuy = plan.getTotalBuyAmount();

        // Exact cent balance: Total sell amount ($2,000.10) must match total buy amount ($999.90 + $1,000.20 = $2,000.10)
        assertThat(totalSell).isEqualByComparingTo(new BigDecimal("2000.10"));
        assertThat(totalBuy).isEqualByComparingTo(new BigDecimal("2000.10"));
        assertThat(totalSell).isEqualByComparingTo(totalBuy);

        // Verify fractional share quantities computed with scale 4
        RebalanceOrder sellA = plan.getSellOrders().get(0);
        assertThat(sellA.stock()).isEqualTo(stockA);
        assertThat(sellA.sharesToTrade()).isEqualByComparingTo(new BigDecimal("66.6700"));
        assertThat(sellA.estimatedAmount()).isEqualByComparingTo(new BigDecimal("2000.10"));

        List<RebalanceOrder> buyOrders = plan.getBuyOrders();
        assertThat(buyOrders).hasSize(2);

        RebalanceOrder buyB = buyOrders.stream().filter(o -> o.stock().equals(stockB)).findFirst().orElseThrow();
        assertThat(buyB.sharesToTrade()).isEqualByComparingTo(new BigDecimal("22.2200"));
        assertThat(buyB.estimatedAmount()).isEqualByComparingTo(new BigDecimal("999.90"));

        RebalanceOrder buyC = buyOrders.stream().filter(o -> o.stock().equals(stockC)).findFirst().orElseThrow();
        assertThat(buyC.sharesToTrade()).isEqualByComparingTo(new BigDecimal("16.6700"));
        assertThat(buyC.estimatedAmount()).isEqualByComparingTo(new BigDecimal("1000.20"));
    }

    // =========================================================================
    // TC_08: Liquidity Ordering (Sells Precede Buys)
    // =========================================================================

    @Test
    @DisplayName("TC_08: Liquidity priority ensures all SELL orders strictly precede any BUY order")
    void TC_08_liquidity_ordering_sells_precede_buys() {
        // Given: Portfolio with 4 assets requiring multiple SELLs and BUYs
        Stock assetA = new Stock("AAA", "Asset Alpha", new BigDecimal("100.00"));
        Stock assetB = new Stock("BBB", "Asset Beta", new BigDecimal("100.00"));
        Stock assetC = new Stock("CCC", "Asset Gamma", new BigDecimal("100.00"));
        Stock assetD = new Stock("DDD", "Asset Delta", new BigDecimal("100.00"));

        // Initial holdings: AAA = 50 ($5,000), BBB = 50 ($5,000). Total = $10,000.
        portfolio.addPosition(assetA, new BigDecimal("50"));
        portfolio.addPosition(assetB, new BigDecimal("50"));

        // Target allocations: AAA 10%, BBB 15%, CCC 35%, DDD 40% (Total: 100%)
        // Demands: SELL AAA ($4,000), SELL BBB ($3,500), BUY CCC ($3,500), BUY DDD ($4,000)
        portfolio.setAllocations(
                new Allocation(assetA, new BigDecimal("0.10")),
                new Allocation(assetB, new BigDecimal("0.15")),
                new Allocation(assetC, new BigDecimal("0.35")),
                new Allocation(assetD, new BigDecimal("0.40"))
        );

        // When: Rebalancing the portfolio
        RebalancePlan plan = portfolio.rebalance();

        // Then:
        List<RebalanceOrder> orders = plan.orders();
        assertThat(orders).hasSize(4);

        int lastSellIndex = -1;
        int firstBuyIndex = Integer.MAX_VALUE;

        for (int i = 0; i < orders.size(); i++) {
            RebalanceOrder order = orders.get(i);
            if (order.isSell()) {
                lastSellIndex = Math.max(lastSellIndex, i);
            } else if (order.isBuy()) {
                firstBuyIndex = Math.min(firstBuyIndex, i);
            }
        }

        // Verify there is at least one SELL and one BUY
        assertThat(lastSellIndex).isGreaterThanOrEqualTo(0);
        assertThat(firstBuyIndex).isLessThan(orders.size());

        // Self-Financing Invariant: The index of every SELL must be strictly less than the index of every BUY
        assertThat(lastSellIndex)
                .as("All SELL orders must precede any BUY order to provide liquidity")
                .isLessThan(firstBuyIndex);

        // Also verify order actions sequentially
        assertThat(orders.get(0).action()).isEqualTo(OrderAction.SELL);
        assertThat(orders.get(1).action()).isEqualTo(OrderAction.SELL);
        assertThat(orders.get(2).action()).isEqualTo(OrderAction.BUY);
        assertThat(orders.get(3).action()).isEqualTo(OrderAction.BUY);
    }

    // =========================================================================
    // TC_09: Current Price Update Dynamically Reflects in Rebalance
    // =========================================================================

    @Test
    @DisplayName("TC_09: Updating stock market price dynamically alters portfolio valuation and rebalance plan")
    void TC_09_current_price_update_reflects_in_rebalance() {
        // Given: Portfolio with 10 META @ $100 ($1,000) and 10 AAPL @ $100 ($1,000). Total: $2,000.
        // Target: 50% META ($1,000) and 50% AAPL ($1,000).
        portfolio.addPosition(meta, new BigDecimal("10"));
        portfolio.addPosition(aapl, new BigDecimal("10"));
        portfolio.setAllocations(
                new Allocation(meta, new BigDecimal("0.50")),
                new Allocation(aapl, new BigDecimal("0.50"))
        );

        // Initially balanced: 0 orders generated
        RebalancePlan initialPlan = portfolio.rebalance();
        assertThat(initialPlan.isEmpty()).isTrue();
        assertThat(initialPlan.getOrderCount()).isZero();
        assertThat(portfolio.getTotalValue()).isEqualByComparingTo(new BigDecimal("2000.00"));

        // When: Market price of META doubles from $100.00 to $200.00
        meta.updateCurrentPrice(new BigDecimal("200.00"));

        // Then:
        // 1. Portfolio total value is dynamically updated: 10 * 200 + 10 * 100 = $3,000.00
        assertThat(portfolio.getTotalValue()).isEqualByComparingTo(new BigDecimal("3000.00"));

        // 2. Rebalancing dynamically produces trade orders reflecting new market conditions
        RebalancePlan updatedPlan = portfolio.rebalance();
        assertThat(updatedPlan.isEmpty()).isFalse();
        assertThat(updatedPlan.getOrderCount()).isEqualTo(2);

        // META target: 50% of $3,000 = $1,500. Currently holds $2,000. Delta: -$500 -> SELL 2.5000 shares
        List<RebalanceOrder> sellOrders = updatedPlan.getSellOrders();
        assertThat(sellOrders).hasSize(1);
        RebalanceOrder sellMeta = sellOrders.get(0);
        assertThat(sellMeta.stock()).isEqualTo(meta);
        assertThat(sellMeta.sharesToTrade()).isEqualByComparingTo(new BigDecimal("2.5000"));
        assertThat(sellMeta.estimatedAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        // AAPL target: 50% of $3,000 = $1,500. Currently holds $1,000. Delta: +$500 -> BUY 5.0000 shares
        List<RebalanceOrder> buyOrders = updatedPlan.getBuyOrders();
        assertThat(buyOrders).hasSize(1);
        RebalanceOrder buyAapl = buyOrders.get(0);
        assertThat(buyAapl.stock()).isEqualTo(aapl);
        assertThat(buyAapl.sharesToTrade()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(buyAapl.estimatedAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // =========================================================================
    // Edge Cases & Additional Domain Contract Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases & Secondary Contracts")
    class EdgeCasesTest {

        @Test
        @DisplayName("Rebalance on empty portfolio returns empty plan without error")
        void emptyPortfolioReturnsEmptyPlan() {
            portfolio.setAllocations(
                    new Allocation(meta, new BigDecimal("0.50")),
                    new Allocation(aapl, new BigDecimal("0.50"))
            );

            RebalancePlan plan = portfolio.rebalance();

            assertThat(plan.isEmpty()).isTrue();
            assertThat(plan.getOrderCount()).isZero();
            assertThat(plan.totalPortfolioValueBefore()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Small price drift within threshold produces no rebalance orders")
        void driftWithinThresholdProducesNoOrders() {
            // Portfolio with 10 META @ $100 ($1,000) and 10 AAPL @ $100 ($1,000). Total: $2,000.
            portfolio.addPosition(meta, 10);
            portfolio.addPosition(aapl, 10);
            portfolio.setAllocations(
                    new Allocation(meta, 0.50),
                    new Allocation(aapl, 0.50)
            );

            // Engine with default threshold ($0.005)
            // A microscopic delta under $0.005 (e.g. $0.001) should produce no trades
            DefaultRebalanceEngine engineWithCustomThreshold = new DefaultRebalanceEngine(new BigDecimal("1.00"));
            Portfolio customPortfolio = new Portfolio(engineWithCustomThreshold);
            customPortfolio.addPosition(meta, 10);
            customPortfolio.addPosition(aapl, 10);
            customPortfolio.setAllocations(
                    new Allocation(meta, 0.50),
                    new Allocation(aapl, 0.50)
            );

            // Small price drift of META from 100 to 100.05 (delta = $0.25 < $1.00 threshold)
            meta.updateCurrentPrice(new BigDecimal("100.05"));

            RebalancePlan plan = customPortfolio.rebalance();
            assertThat(plan.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Rebalance without configured allocations throws IllegalStateException")
        void rebalanceWithoutAllocationsThrowsException() {
            portfolio.addPosition(meta, 10);

            assertThatThrownBy(() -> portfolio.rebalance())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot rebalance: no target allocations defined");
        }

        @Test
        @DisplayName("Adding position accumulates existing shares for same stock symbol")
        void addPositionAccumulatesShares() {
            portfolio.addPosition(meta, new BigDecimal("5"));
            portfolio.addPosition(new Stock("meta", "Meta Lowercase", new BigDecimal("100.00")), new BigDecimal("3"));

            PortfolioPosition pos = portfolio.getPosition("META").orElseThrow();
            assertThat(pos.shares()).isEqualByComparingTo(new BigDecimal("8"));
            assertThat(pos.getMarketValue()).isEqualByComparingTo(new BigDecimal("800.00"));
        }

        @Test
        @DisplayName("RebalancePlan precomputed record components are consistent and immutable")
        void rebalancePlanPrecomputedComponentsAreConsistentAndImmutable() {
            portfolio.addPosition(meta, 10);
            portfolio.addPosition(aapl, 10);
            portfolio.setAllocations(
                    new Allocation(meta, 0.40),
                    new Allocation(aapl, 0.60)
            );

            RebalancePlan plan = portfolio.rebalance();

            // Direct record components match getters (O(1) access)
            assertThat(plan.sellOrders()).isEqualTo(plan.getSellOrders()).hasSize(1);
            assertThat(plan.buyOrders()).isEqualTo(plan.getBuyOrders()).hasSize(1);
            assertThat(plan.totalSellAmount()).isEqualByComparingTo(plan.getTotalSellAmount());
            assertThat(plan.totalBuyAmount()).isEqualByComparingTo(plan.getTotalBuyAmount());

            // Immutability: attempting to mutate precomputed lists throws UnsupportedOperationException
            assertThatThrownBy(() -> plan.sellOrders().add(plan.buyOrders().get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> plan.buyOrders().add(plan.sellOrders().get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
