package bot.trade.analytics

import bot.trade.exchanges.clients.Candlestick
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode

data class GridSuitabilityResult(
    val exchange: String,
    val pair: String,
    val currentPrice: BigDecimal,
    val priceMin: BigDecimal,
    val priceMax: BigDecimal,
    val priceRangePct: BigDecimal,
    val avgDailyVolatilityPct: BigDecimal,
    val avgDailyVolume: BigDecimal,
    val candlesAnalyzed: Int,
    val periodDays: Int,
    val suggestedLowerBound: BigDecimal,
    val suggestedUpperBound: BigDecimal,
    val suggestedOrderDistancePct: BigDecimal,
    val suggestedProfitDistancePct: BigDecimal,
    val estimatedFillsPerDay: BigDecimal,
    val numGridLevels: BigDecimal,
    val estimatedDailyReturnPct: BigDecimal,
    val estimatedMonthlyReturnPct: BigDecimal,
    val suitabilityScore: Int,
    val suitabilityLabel: String,
    val error: String? = null
) {
    companion object {
        fun error(exchange: String, pair: String, msg: String) = GridSuitabilityResult(
            exchange = exchange,
            pair = pair,
            currentPrice = BigDecimal.ZERO,
            priceMin = BigDecimal.ZERO,
            priceMax = BigDecimal.ZERO,
            priceRangePct = BigDecimal.ZERO,
            avgDailyVolatilityPct = BigDecimal.ZERO,
            avgDailyVolume = BigDecimal.ZERO,
            candlesAnalyzed = 0,
            periodDays = 0,
            suggestedLowerBound = BigDecimal.ZERO,
            suggestedUpperBound = BigDecimal.ZERO,
            suggestedOrderDistancePct = BigDecimal.ZERO,
            suggestedProfitDistancePct = BigDecimal.ZERO,
            estimatedFillsPerDay = BigDecimal.ZERO,
            numGridLevels = BigDecimal.ZERO,
            estimatedDailyReturnPct = BigDecimal.ZERO,
            estimatedMonthlyReturnPct = BigDecimal.ZERO,
            suitabilityScore = 0,
            suitabilityLabel = "Error",
            error = msg
        )
    }
}

class GridSuitabilityService {

    private val log = KotlinLogging.logger {}

    fun analyze(exchange: String, pair: String, candles: List<Candlestick>): GridSuitabilityResult {
        if (candles.isEmpty()) {
            return GridSuitabilityResult.error(exchange, pair, "No candle data returned for pair '$pair'")
        }

        val scale = 8

        val currentPrice = candles.last().close
        val priceMin = candles.minOf { it.low }
        val priceMax = candles.maxOf { it.high }

        val priceRangePct = (priceMax - priceMin)
            .divide(priceMin, scale, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)

        // Detect interval from first two candles
        val intervalMs = if (candles.size >= 2) candles[1].openTime - candles[0].openTime else 3_600_000L
        val candlesPerDay = (86_400_000L / intervalMs).toInt().coerceAtLeast(1)
        val periodDays = (candles.size.toDouble() / candlesPerDay).coerceAtLeast(1.0).toInt()

        val dailyVolatilities = mutableListOf<BigDecimal>()
        val dailyVolumes = mutableListOf<BigDecimal>()

        candles.chunked(candlesPerDay) { dayCandles ->
            val dayHigh = dayCandles.maxOf { it.high }
            val dayLow = dayCandles.minOf { it.low }
            val dayVol = dayCandles.sumOf { it.volume }
            if (dayLow > BigDecimal.ZERO) {
                val volatility = (dayHigh - dayLow)
                    .divide(dayLow, scale, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                dailyVolatilities.add(volatility)
            }
            dailyVolumes.add(dayVol)
        }

        val avgDailyVolatilityPct = if (dailyVolatilities.isNotEmpty())
            dailyVolatilities.reduce(BigDecimal::add)
                .divide(BigDecimal(dailyVolatilities.size), 2, RoundingMode.HALF_UP)
        else BigDecimal.ZERO

        val avgDailyVolume = if (dailyVolumes.isNotEmpty())
            dailyVolumes.reduce(BigDecimal::add)
                .divide(BigDecimal(dailyVolumes.size), 2, RoundingMode.HALF_UP)
        else BigDecimal.ZERO

        // Suggested grid parameters
        val suggestedOrderDistancePct = avgDailyVolatilityPct
            .divide(BigDecimal(3), 2, RoundingMode.HALF_UP)
            .coerceAtLeast(BigDecimal("0.10"))

        val suggestedProfitDistancePct = suggestedOrderDistancePct
            .multiply(BigDecimal("0.8"))
            .setScale(2, RoundingMode.HALF_UP)

        val halfRange = (priceMax - priceMin).divide(BigDecimal(2), scale, RoundingMode.HALF_UP)
        val suggestedLowerBound = (currentPrice - halfRange).setScale(currentPrice.scale().coerceAtLeast(2), RoundingMode.HALF_UP)
        val suggestedUpperBound = (currentPrice + halfRange).setScale(currentPrice.scale().coerceAtLeast(2), RoundingMode.HALF_UP)

        val estimatedFillsPerDay = if (suggestedOrderDistancePct > BigDecimal.ZERO)
            avgDailyVolatilityPct
                .divide(suggestedOrderDistancePct, 1, RoundingMode.HALF_UP)
                .multiply(BigDecimal(2))
        else BigDecimal.ZERO

        // Estimated earnings:
        // numGridLevels = priceRangePct / orderDistancePct
        // Each completed grid cycle (1 buy + 1 sell) earns profitDistancePct% of (capital / numLevels)
        // Daily cycles = estimatedFillsPerDay / 2
        // dailyReturnPct = (cycles/day) * profitDistancePct / numGridLevels
        val numGridLevels = if (suggestedOrderDistancePct > BigDecimal.ZERO)
            priceRangePct.divide(suggestedOrderDistancePct, 1, RoundingMode.HALF_UP)
                .coerceAtLeast(BigDecimal.ONE)
        else BigDecimal("20")

        val estimatedDailyReturnPct = if (numGridLevels > BigDecimal.ZERO)
            estimatedFillsPerDay
                .divide(BigDecimal(2), 4, RoundingMode.HALF_UP)
                .multiply(suggestedProfitDistancePct)
                .divide(numGridLevels, 4, RoundingMode.HALF_UP)
                .setScale(3, RoundingMode.HALF_UP)
        else BigDecimal.ZERO

        val estimatedMonthlyReturnPct = estimatedDailyReturnPct
            .multiply(BigDecimal(30))
            .setScale(2, RoundingMode.HALF_UP)

        // Suitability score 0-100
        val volatilityScore = when {
            avgDailyVolatilityPct < BigDecimal("1") -> 10
            avgDailyVolatilityPct < BigDecimal("2") -> 30
            avgDailyVolatilityPct < BigDecimal("5") -> 60
            avgDailyVolatilityPct < BigDecimal("10") -> 90
            avgDailyVolatilityPct < BigDecimal("20") -> 100
            avgDailyVolatilityPct < BigDecimal("40") -> 80
            else -> 50
        }

        val rangeScore = when {
            priceRangePct < BigDecimal("5") -> 20
            priceRangePct < BigDecimal("15") -> 60
            priceRangePct < BigDecimal("40") -> 90
            priceRangePct < BigDecimal("80") -> 100
            else -> 70
        }

        val suitabilityScore = (volatilityScore * 0.6 + rangeScore * 0.4).toInt()

        val suitabilityLabel = when {
            suitabilityScore >= 85 -> "Excellent"
            suitabilityScore >= 70 -> "Good"
            suitabilityScore >= 50 -> "Fair"
            suitabilityScore >= 30 -> "Poor"
            else -> "Not Suitable"
        }

        log.info("Grid suitability for $exchange/$pair: score=$suitabilityScore ($suitabilityLabel), " +
                "volatility=${avgDailyVolatilityPct}%, range=${priceRangePct}%, " +
                "dailyReturn=${estimatedDailyReturnPct}%")

        return GridSuitabilityResult(
            exchange = exchange,
            pair = pair,
            currentPrice = currentPrice,
            priceMin = priceMin,
            priceMax = priceMax,
            priceRangePct = priceRangePct,
            avgDailyVolatilityPct = avgDailyVolatilityPct,
            avgDailyVolume = avgDailyVolume,
            candlesAnalyzed = candles.size,
            periodDays = periodDays,
            suggestedLowerBound = suggestedLowerBound,
            suggestedUpperBound = suggestedUpperBound,
            suggestedOrderDistancePct = suggestedOrderDistancePct,
            suggestedProfitDistancePct = suggestedProfitDistancePct,
            estimatedFillsPerDay = estimatedFillsPerDay,
            numGridLevels = numGridLevels,
            estimatedDailyReturnPct = estimatedDailyReturnPct,
            estimatedMonthlyReturnPct = estimatedMonthlyReturnPct,
            suitabilityScore = suitabilityScore,
            suitabilityLabel = suitabilityLabel
        )
    }

    private fun <T : Comparable<T>> T.coerceAtLeast(min: T): T = if (this < min) min else this
}
