package bot.trade.analytics

import exchange_api.gate.rest.client.GateTradeResponse
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Service for analyzing grid bot trading profits.
 *
 * Algorithm:
 * 1. Find price range (min/max executed prices)
 * 2. Find most common grid step (distance between BUY and SELL orders)
 * 3. Match BUY-SELL pairs and calculate profit
 */
class GridAnalyticsService {

    private val log = KotlinLogging.logger {}

    /**
     * Analyze trades and calculate grid bot profit.
     *
     * @param trades List of trades from exchange
     * @param orderValueMin Minimum order value in USDT to include (optional filter)
     * @param orderValueMax Maximum order value in USDT to include (optional filter)
     * @return GridAnalyticsResult with profit calculations
     */
    fun analyzeTrades(
        trades: List<GateTradeResponse>,
        orderValueMin: BigDecimal? = null,
        orderValueMax: BigDecimal? = null
    ): GridAnalyticsResult {
        log.info("Analyzing ${trades.size} trades")

        // Filter by order VALUE (amount × price) in USDT if specified
        val filteredTrades = trades.filter { trade ->
            val orderValue = trade.amount * trade.price  // Total value in USDT
            val valueOk = (orderValueMin == null || orderValue >= orderValueMin) &&
                          (orderValueMax == null || orderValue <= orderValueMax)
            valueOk
        }

        log.info("After filtering: ${filteredTrades.size} trades")

        if (filteredTrades.isEmpty()) {
            return GridAnalyticsResult.empty()
        }

        // Separate BUY and SELL trades
        val buyTrades = filteredTrades.filter { it.side.equals("Buy", ignoreCase = true) }
            .sortedBy { it.price }
        val sellTrades = filteredTrades.filter { it.side.equals("Sell", ignoreCase = true) }
            .sortedBy { it.price }

        log.info("BUY trades: ${buyTrades.size}, SELL trades: ${sellTrades.size}")

        // Find price range
        val allPrices = filteredTrades.map { it.price }
        val minPrice = allPrices.minOrNull() ?: BigDecimal.ZERO
        val maxPrice = allPrices.maxOrNull() ?: BigDecimal.ZERO

        log.info("Price range: $minPrice - $maxPrice")

        // Calculate time range
        val timestamps = filteredTrades.map { it.timestamp }
        val firstTradeTime = timestamps.minOrNull() ?: 0L
        val lastTradeTime = timestamps.maxOrNull() ?: 0L
        val tradingDurationMs = if (lastTradeTime > firstTradeTime) lastTradeTime - firstTradeTime else 0L
        val tradingDurationHours = tradingDurationMs / (1000.0 * 60 * 60)

        log.info("Trading period: ${tradingDurationHours.toLong()}h (from $firstTradeTime to $lastTradeTime)")

        // Find most common grid step
        val gridStep = findMostCommonGridStep(buyTrades, sellTrades)
        log.info("Detected grid step: $gridStep")

        // Match BUY-SELL pairs and calculate profit (old method - kept for comparison)
        val (pairs, pairProfit, pairFees) = matchPairsAndCalculateProfit(buyTrades, sellTrades, gridStep)
        log.info("Pair matching: ${pairs.size} pairs, profit: $pairProfit, fees: $pairFees")

        // NEW: Calculate profit using average prices method
        val avgPriceResult = calculateProfitByAveragePrice(buyTrades, sellTrades)
        log.info("Average price method: avgBuyPrice=${avgPriceResult.avgBuyPrice}, avgSellPrice=${avgPriceResult.avgSellPrice}, " +
                "matchedVolume=${avgPriceResult.matchedVolume}, grossProfit=${avgPriceResult.grossProfit}")

        // Use the average price method as primary
        val totalProfit = avgPriceResult.grossProfit
        val totalFees = avgPriceResult.totalFees
        val netProfit = totalProfit - totalFees
        val totalInvested = avgPriceResult.matchedVolume * avgPriceResult.avgBuyPrice

        // Calculate ROI and rates
        val roi = if (totalInvested > BigDecimal.ZERO) {
            netProfit.divide(totalInvested, 6, RoundingMode.HALF_UP) * BigDecimal(100)
        } else BigDecimal.ZERO

        val profitPerHour = if (tradingDurationHours > 0) {
            netProfit.divide(BigDecimal(tradingDurationHours), 8, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val profitPerDay = profitPerHour * BigDecimal(24)

        // Calculate average trade frequency (trades per hour)
        val tradesPerHour = if (tradingDurationHours > 0) {
            filteredTrades.size / tradingDurationHours
        } else 0.0

        log.info("ROI: $roi%, profitPerHour: $profitPerHour, tradesPerHour: $tradesPerHour")

        return GridAnalyticsResult(
            totalTrades = filteredTrades.size,
            buyTrades = buyTrades.size,
            sellTrades = sellTrades.size,
            minPrice = minPrice,
            maxPrice = maxPrice,
            gridStep = gridStep,
            matchedPairs = pairs.size,
            grossProfit = totalProfit,
            totalFees = totalFees,
            netProfit = netProfit,
            pairs = pairs,
            unmatchedBuys = buyTrades.size - pairs.count { it.buyTrade != null },
            unmatchedSells = sellTrades.size - pairs.count { it.sellTrade != null },
            // New metrics
            totalInvested = totalInvested,
            roi = roi,
            profitPerHour = profitPerHour,
            profitPerDay = profitPerDay,
            tradesPerHour = tradesPerHour,
            firstTradeTime = firstTradeTime,
            lastTradeTime = lastTradeTime,
            tradingDurationHours = tradingDurationHours,
            // Average price calculation details
            avgBuyPrice = avgPriceResult.avgBuyPrice,
            avgSellPrice = avgPriceResult.avgSellPrice,
            totalBuyVolume = avgPriceResult.totalBuyVolume,
            totalSellVolume = avgPriceResult.totalSellVolume,
            matchedVolume = avgPriceResult.matchedVolume,
            unmatchedVolume = avgPriceResult.unmatchedVolume
        )
    }

    /**
     * Calculate profit using average buy/sell prices method.
     *
     * Formula:
     * - Avg Buy Price = Σ(buy_amount × buy_price) / Σ(buy_amount)
     * - Avg Sell Price = Σ(sell_amount × sell_price) / Σ(sell_amount)
     * - Matched Volume = min(total_buy_volume, total_sell_volume)
     * - Gross Profit = matchedVolume × (avgSellPrice - avgBuyPrice)
     *
     * The unmatched volume represents tokens still in position (unrealized P&L).
     */
    private fun calculateProfitByAveragePrice(
        buyTrades: List<GateTradeResponse>,
        sellTrades: List<GateTradeResponse>
    ): AveragePriceResult {
        // Calculate total volumes
        val totalBuyVolume = buyTrades.sumOf { it.amount }
        val totalSellVolume = sellTrades.sumOf { it.amount }

        // Calculate weighted average prices
        val totalBuyCost = buyTrades.sumOf { it.amount * it.price }  // Σ(amount × price)
        val totalSellRevenue = sellTrades.sumOf { it.amount * it.price }

        val avgBuyPrice = if (totalBuyVolume > BigDecimal.ZERO) {
            totalBuyCost.divide(totalBuyVolume, 10, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val avgSellPrice = if (totalSellVolume > BigDecimal.ZERO) {
            totalSellRevenue.divide(totalSellVolume, 10, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        // Matched volume = min of both sides (completed cycles)
        val matchedVolume = totalBuyVolume.min(totalSellVolume)
        val unmatchedVolume = (totalBuyVolume - totalSellVolume).abs()

        // Calculate gross profit on matched volume
        val grossProfit = matchedVolume * (avgSellPrice - avgBuyPrice)

        // Calculate fees proportionally based on matched volume
        val buyFees = buyTrades.sumOf { it.fee }
        val sellFees = sellTrades.sumOf { it.fee }

        // Proportional fees based on what volume was matched
        val buyFeeRatio = if (totalBuyVolume > BigDecimal.ZERO) {
            matchedVolume.divide(totalBuyVolume, 10, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val sellFeeRatio = if (totalSellVolume > BigDecimal.ZERO) {
            matchedVolume.divide(totalSellVolume, 10, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val totalFees = (buyFees * buyFeeRatio) + (sellFees * sellFeeRatio)

        return AveragePriceResult(
            avgBuyPrice = avgBuyPrice,
            avgSellPrice = avgSellPrice,
            totalBuyVolume = totalBuyVolume,
            totalSellVolume = totalSellVolume,
            matchedVolume = matchedVolume,
            unmatchedVolume = unmatchedVolume,
            grossProfit = grossProfit,
            totalFees = totalFees
        )
    }

    /**
     * Find the most common price distance between BUY and SELL orders.
     * This represents the grid step size.
     */
    private fun findMostCommonGridStep(
        buyTrades: List<GateTradeResponse>,
        sellTrades: List<GateTradeResponse>
    ): BigDecimal {
        if (buyTrades.isEmpty() || sellTrades.isEmpty()) {
            return BigDecimal.ZERO
        }

        // Calculate distances between each buy and nearby sell prices
        val distances = mutableListOf<BigDecimal>()

        for (buy in buyTrades) {
            for (sell in sellTrades) {
                if (sell.price > buy.price) {
                    val distance = sell.price - buy.price
                    // Only consider reasonable grid distances (0.1% to 10% of price)
                    val minReasonable = buy.price * BigDecimal("0.001")
                    val maxReasonable = buy.price * BigDecimal("0.10")
                    if (distance >= minReasonable && distance <= maxReasonable) {
                        distances.add(distance)
                    }
                }
            }
        }

        if (distances.isEmpty()) {
            // Fallback: calculate from consecutive prices
            val allPrices = (buyTrades.map { it.price } + sellTrades.map { it.price })
                .distinct()
                .sorted()

            if (allPrices.size < 2) return BigDecimal.ZERO

            val consecutiveDistances = allPrices.zipWithNext { a, b -> b - a }
            return findMostFrequentValue(consecutiveDistances)
        }

        return findMostFrequentValue(distances)
    }

    /**
     * Find the most frequently occurring value in a list (with tolerance for rounding).
     */
    private fun findMostFrequentValue(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO

        // Round to reasonable precision for grouping
        val scale = 6
        val rounded = values.map { it.setScale(scale, RoundingMode.HALF_UP) }

        // Count occurrences
        val counts = rounded.groupingBy { it }.eachCount()

        // Find most common
        return counts.maxByOrNull { it.value }?.key ?: BigDecimal.ZERO
    }

    /**
     * Match BUY-SELL pairs based on grid step and calculate profit.
     *
     * A valid pair is when:
     * - SELL price is approximately gridStep higher than BUY price
     * - The amounts are similar (within 10% tolerance)
     */
    private fun matchPairsAndCalculateProfit(
        buyTrades: List<GateTradeResponse>,
        sellTrades: List<GateTradeResponse>,
        gridStep: BigDecimal
    ): Triple<List<TradePair>, BigDecimal, BigDecimal> {
        val pairs = mutableListOf<TradePair>()
        var totalProfit = BigDecimal.ZERO
        var totalFees = BigDecimal.ZERO

        // Track which trades have been matched
        val unmatchedBuys = buyTrades.toMutableList()
        val unmatchedSells = sellTrades.toMutableList()

        // Tolerance for price matching (5% of grid step)
        val priceTolerance = gridStep * BigDecimal("0.05")

        // Try to match each buy with a corresponding sell
        val buysToRemove = mutableListOf<GateTradeResponse>()

        for (buy in unmatchedBuys) {
            val expectedSellPrice = buy.price + gridStep

            // Find matching sell
            val matchingSell = unmatchedSells.find { sell ->
                val priceDiff = (sell.price - expectedSellPrice).abs()
                val amountRatio = if (buy.amount > BigDecimal.ZERO) {
                    sell.amount.divide(buy.amount, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ONE

                // Price within tolerance and amounts within 10%
                priceDiff <= priceTolerance &&
                amountRatio >= BigDecimal("0.90") &&
                amountRatio <= BigDecimal("1.10")
            }

            if (matchingSell != null) {
                // Calculate profit for this pair
                val buyTotal = buy.amount * buy.price
                val sellTotal = matchingSell.amount * matchingSell.price
                val pairProfit = sellTotal - buyTotal
                val pairFees = buy.fee + matchingSell.fee

                pairs.add(TradePair(
                    buyTrade = buy,
                    sellTrade = matchingSell,
                    profit = pairProfit,
                    fees = pairFees,
                    netProfit = pairProfit - pairFees
                ))

                totalProfit += pairProfit
                totalFees += pairFees

                buysToRemove.add(buy)
                unmatchedSells.remove(matchingSell)
            }
        }

        unmatchedBuys.removeAll(buysToRemove)

        return Triple(pairs, totalProfit, totalFees)
    }
}

/**
 * Result of average price profit calculation.
 */
data class AveragePriceResult(
    val avgBuyPrice: BigDecimal,
    val avgSellPrice: BigDecimal,
    val totalBuyVolume: BigDecimal,
    val totalSellVolume: BigDecimal,
    val matchedVolume: BigDecimal,      // min(buyVolume, sellVolume) - completed cycles
    val unmatchedVolume: BigDecimal,    // abs(buyVolume - sellVolume) - still in position
    val grossProfit: BigDecimal,
    val totalFees: BigDecimal
)

/**
 * Result of grid analytics calculation.
 */
data class GridAnalyticsResult(
    val totalTrades: Int,
    val buyTrades: Int,
    val sellTrades: Int,
    val minPrice: BigDecimal,
    val maxPrice: BigDecimal,
    val gridStep: BigDecimal,
    val matchedPairs: Int,
    val grossProfit: BigDecimal,
    val totalFees: BigDecimal,
    val netProfit: BigDecimal,
    val pairs: List<TradePair>,
    val unmatchedBuys: Int,
    val unmatchedSells: Int,
    // Additional metrics
    val totalInvested: BigDecimal = BigDecimal.ZERO,
    val roi: BigDecimal = BigDecimal.ZERO,              // ROI in percentage
    val profitPerHour: BigDecimal = BigDecimal.ZERO,
    val profitPerDay: BigDecimal = BigDecimal.ZERO,
    val tradesPerHour: Double = 0.0,
    val firstTradeTime: Long = 0L,                      // Timestamp in ms
    val lastTradeTime: Long = 0L,                       // Timestamp in ms
    val tradingDurationHours: Double = 0.0,
    // Average price calculation details
    val avgBuyPrice: BigDecimal = BigDecimal.ZERO,
    val avgSellPrice: BigDecimal = BigDecimal.ZERO,
    val totalBuyVolume: BigDecimal = BigDecimal.ZERO,   // Total tokens bought
    val totalSellVolume: BigDecimal = BigDecimal.ZERO,  // Total tokens sold
    val matchedVolume: BigDecimal = BigDecimal.ZERO,    // min(buy, sell) - completed cycles
    val unmatchedVolume: BigDecimal = BigDecimal.ZERO   // Tokens still in position
) {
    companion object {
        fun empty() = GridAnalyticsResult(
            totalTrades = 0,
            buyTrades = 0,
            sellTrades = 0,
            minPrice = BigDecimal.ZERO,
            maxPrice = BigDecimal.ZERO,
            gridStep = BigDecimal.ZERO,
            matchedPairs = 0,
            grossProfit = BigDecimal.ZERO,
            totalFees = BigDecimal.ZERO,
            netProfit = BigDecimal.ZERO,
            pairs = emptyList(),
            unmatchedBuys = 0,
            unmatchedSells = 0,
            totalInvested = BigDecimal.ZERO,
            roi = BigDecimal.ZERO,
            profitPerHour = BigDecimal.ZERO,
            profitPerDay = BigDecimal.ZERO,
            tradesPerHour = 0.0,
            firstTradeTime = 0L,
            lastTradeTime = 0L,
            tradingDurationHours = 0.0,
            avgBuyPrice = BigDecimal.ZERO,
            avgSellPrice = BigDecimal.ZERO,
            totalBuyVolume = BigDecimal.ZERO,
            totalSellVolume = BigDecimal.ZERO,
            matchedVolume = BigDecimal.ZERO,
            unmatchedVolume = BigDecimal.ZERO
        )
    }

    val priceRange: BigDecimal get() = maxPrice - minPrice
    val profitPerPair: BigDecimal get() = if (matchedPairs > 0) {
        netProfit.divide(BigDecimal(matchedPairs), 8, RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    // Completed cycles = matched pairs (each pair is one buy-sell cycle)
    val completedCycles: Int get() = matchedPairs

    // Spread between average prices (in percentage)
    val avgPriceSpread: BigDecimal get() = if (avgBuyPrice > BigDecimal.ZERO) {
        (avgSellPrice - avgBuyPrice).divide(avgBuyPrice, 6, RoundingMode.HALF_UP) * BigDecimal(100)
    } else BigDecimal.ZERO
}

/**
 * A matched BUY-SELL pair from grid trading.
 */
data class TradePair(
    val buyTrade: GateTradeResponse?,
    val sellTrade: GateTradeResponse?,
    val profit: BigDecimal,
    val fees: BigDecimal,
    val netProfit: BigDecimal
)