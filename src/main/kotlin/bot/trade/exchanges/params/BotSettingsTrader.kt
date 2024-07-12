package bot.trade.exchanges.params

import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.clients.TradePair
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class BotSettingsTrader(
    @SerializedName("flow_name") override val name: String,
    @SerializedName("symbol") override val pair: TradePair,
    @SerializedName("exchange_type") override val exchange: String,
    orderBalanceType: String = "first", // if first => BTC balance, else second => USDT balance (default = second)
    countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    @SerializedName("strategy_type") val strategy: StrategyType,
    @SerializedName("order_type") val ordersType: TYPE,
    @SerializedName("parameters") val parameters: TradeParameters,
    @SerializedName("trend_detector") val trendDetector: TrendDetector? = null,
    @SerializedName("min_order_amount") val minOrderAmount: MinOrderAmount? = null,
    @SerializedName("market_type") val marketType: String,
    @SerializedName("market_type_comment") val marketTypeComment: String,
    @SerializedName("strategy_type_comment") val strategyTypeComment: String,
    @SerializedName("auto_balance") val autoBalance: Boolean = false
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange.uppercase(),
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
) {

    class MinOrderAmount(
        @SerializedName("amount") val amount: BigDecimal,
        @SerializedName("countOfDigitsAfterDotForAmount") val countOfDigitsAfterDotForAmount: Int = 0
    )

    enum class StrategyType { LONG, SHORT, BOTH }

    class TrendDetector(
        @SerializedName("not_auto_calc_trend") val notAutoCalcTrend: Boolean = false,
        @SerializedName("rsi1") val rsi1: Rsi,
        @SerializedName("rsi2") val rsi2: Rsi,
        @SerializedName("hma_parameters") val hmaParameters: HmaParameters,
        @SerializedName("input_kline_interval") val inputKlineInterval: String?
    ) {
        class Rsi(
            @SerializedName("rsi_period") val rsiPeriod: Int,
            @SerializedName("time_frame") val timeFrame: String
        )

        class HmaParameters(
            @SerializedName("hma1_period") val hma1Period: Int,
            @SerializedName("hma2_period") val hma2Period: Int,
            @SerializedName("hma3_period") val hma3Period: Int,
            @SerializedName("time_frame") val timeFrame: String
        )
    }

    class TradeParameters(
        @SerializedName("long_parameters") val longParameters: Parameters?,
        @SerializedName("short_parameters") val shortParameters: Parameters?,
    ) {
        class Parameters(
            @SerializedName("trading_range") val tradingRange: TradingRange, // Trading Range:: range of price for orders
            @SerializedName("in_order_quantity") val inOrderQuantity: Param, // Order Quantity:: order size
            @SerializedName("in_order_distance") val inOrderDistance: Param, // Order Distance:: distance between every order
            @SerializedName("trailing_in_order_distance") val trailingInOrderDistance: Param?, // Trailing in Distance:: distance between in_order and when_order_be_executed
            @SerializedName("trigger_in_order_distance") val triggerInOrderDistance: Param?, // Trigger in Distance
            @SerializedName("trigger_distance") val triggerDistance: Param, // Trigger Distance:: distance between order and stop-order
            @SerializedName("min_tp_distance") val minTpDistance: Param,
            @SerializedName("max_tp_distance") val maxTpDistance: Param,
            @SerializedName("max_trigger_count") val orderMaxQuantity: Int, // Max Order count:: max amount of orders
            @SerializedName("set_close_orders") val setCloseOrders: Boolean = true, // set close position orders when bot starts
            @SerializedName("counter_distance") val counterDistance: BigDecimal? = null,
            @SerializedName("use_realized_pnl_in_calc_profit") val withRealizedPnl: Boolean? = null,
            @SerializedName("entire_tp") val entireTp: EntireTp?
        ) {

            class EntireTp(
                @SerializedName("max_trigger_amount") val maxTriggerAmount: Int,
                @SerializedName("max_profit_percent") val maxProfitPercent: BigDecimal,
                @SerializedName("max_loss_percent") val maxLossPercent: BigDecimal,
                @SerializedName("enabled_in_hedge") val enabledInHedge: Boolean,
                @SerializedName("enabled") val enabled: Boolean,
                @SerializedName("tp_distance") val tpDistance: Param
            )


            fun minRange() = tradingRange.lowerBound
            fun maxRange() = tradingRange.upperBound
            fun orderDistance() = inOrderDistance
            fun orderQuantity() = inOrderQuantity.value
            fun triggerDistance() = triggerDistance.value
            fun orderMaxQuantity() = orderMaxQuantity
            fun triggerInOrderDistance() = triggerInOrderDistance?.value
            fun minTpDistance() = minTpDistance.value
            fun maxTpDistance() = maxTpDistance.value
            fun trailingInOrderDistance() = trailingInOrderDistance?.value
            fun setCloseOrders() = setCloseOrders
        }
    }
}