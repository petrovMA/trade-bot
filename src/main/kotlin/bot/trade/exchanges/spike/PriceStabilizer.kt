package bot.trade.exchanges.spike

import java.math.BigDecimal
import java.math.RoundingMode

class PriceStabilizer(private val config: SpikeConfig) {

    private val priceWindow = mutableListOf<TimestampedPrice>()

    data class TimestampedPrice(
        val price: BigDecimal,
        val time: Long
    )

    fun recordPrice(price: BigDecimal, now: Long = System.currentTimeMillis()) {
        priceWindow.add(TimestampedPrice(price, now))
        priceWindow.removeAll { now - it.time > config.stabilizationWindowMs }
    }

    fun isStable(sinceTime: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (now - sinceTime < config.stabilizationDelayMs) return false
        if (priceWindow.size < config.minPricePoints) return false

        val prices = priceWindow.map { it.price }
        val maxPrice = prices.max()
        val minPrice = prices.min()

        if (minPrice <= BigDecimal.ZERO) return false

        val volatility = (maxPrice - minPrice)
            .divide(minPrice, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))

        return volatility <= config.stabilizationTolerancePercent
    }

    fun getMedianPrice(): BigDecimal {
        if (priceWindow.isEmpty()) return BigDecimal.ZERO
        val sorted = priceWindow.map { it.price }.sorted()
        return sorted[sorted.size / 2]
    }

    fun isTimedOut(sinceTime: Long, now: Long = System.currentTimeMillis()): Boolean =
        now - sinceTime > config.maxWaitTimeMs

    fun reset() {
        priceWindow.clear()
    }
}
