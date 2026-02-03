package bot.trade.exchanges.spike

import com.typesafe.config.Config
import java.math.BigDecimal

data class SpikeConfig(
    val enabled: Boolean = false,
    val threshold: Int = 3,
    val timeWindowMs: Long = 10_000,
    val stabilizationDelayMs: Long = 15_000,
    val stabilizationWindowMs: Long = 10_000,
    val stabilizationTolerancePercent: BigDecimal = BigDecimal("0.3"),
    val minPricePoints: Int = 5,
    val maxWaitTimeMs: Long = 120_000,
    val maxPriceDriftPercent: BigDecimal = BigDecimal("3.0"),
    val aggregateOrderType: String = "LIMIT",
    val limitPriceOffsetPercent: BigDecimal = BigDecimal("0.1")
) {
    companion object {
        fun disabled() = SpikeConfig(enabled = false)

        fun fromConfig(config: Config): SpikeConfig {
            val sa = config.getConfig("spike_aggregation")
            return SpikeConfig(
                enabled = sa.getBoolean("enabled"),
                threshold = sa.getInt("threshold"),
                timeWindowMs = sa.getDuration("time_window").toMillis(),
                stabilizationDelayMs = sa.getDuration("stabilization_delay").toMillis(),
                stabilizationWindowMs = sa.getDuration("stabilization_window").toMillis(),
                stabilizationTolerancePercent = BigDecimal(sa.getDouble("stabilization_tolerance_percent").toString()),
                minPricePoints = sa.getInt("min_price_points"),
                maxWaitTimeMs = sa.getDuration("max_wait_time").toMillis(),
                maxPriceDriftPercent = BigDecimal(sa.getDouble("max_price_drift_percent").toString()),
                aggregateOrderType = sa.getString("aggregate_order_type"),
                limitPriceOffsetPercent = BigDecimal(sa.getDouble("limit_price_offset_percent").toString())
            )
        }
    }
}
