package bot.trade.exchanges.spike

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.SIDE
import java.math.BigDecimal
import java.math.RoundingMode

class SpikeDetector(private val config: SpikeConfig) {

    private val recentFills = mutableListOf<TimestampedFill>()
    private var spikeStartTime: Long? = null

    data class TimestampedFill(
        val order: ActiveOrder,
        val fillPrice: BigDecimal,
        val fillTime: Long
    )

    fun registerFill(order: ActiveOrder, fillPrice: BigDecimal, now: Long = System.currentTimeMillis()): Boolean {
        recentFills.removeAll { now - it.fillTime > config.timeWindowMs }

        recentFills.add(TimestampedFill(order, fillPrice, now))

        val sameSideFills = recentFills.filter { it.order.orderSide == order.orderSide }

        if (sameSideFills.size >= config.threshold) {
            if (spikeStartTime == null) {
                spikeStartTime = sameSideFills.first().fillTime
            }
            return true
        }
        return false
    }

    fun getBufferedFills(): List<TimestampedFill> = recentFills.toList()

    fun getTotalAmount(): BigDecimal =
        recentFills.mapNotNull { it.order.amount }.fold(BigDecimal.ZERO, BigDecimal::add)

    fun getWeightedAvgPrice(): BigDecimal {
        val totalValue = recentFills.fold(BigDecimal.ZERO) { acc, fill ->
            acc + fill.fillPrice * (fill.order.amount ?: BigDecimal.ZERO)
        }
        val totalAmount = getTotalAmount()
        return if (totalAmount > BigDecimal.ZERO)
            totalValue.divide(totalAmount, 8, RoundingMode.HALF_UP)
        else BigDecimal.ZERO
    }

    fun reset() {
        recentFills.clear()
        spikeStartTime = null
    }

    fun isActive(): Boolean = spikeStartTime != null

    fun getSpikeStartTime(): Long? = spikeStartTime
}
