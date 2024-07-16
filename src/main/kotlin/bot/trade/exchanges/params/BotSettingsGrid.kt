package bot.trade.exchanges.params

import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.clients.TradePair
import com.google.gson.annotations.SerializedName

class BotSettingsGrid(
    @SerializedName("flow_name") override val name: String,
    @SerializedName("symbol") override val pair: TradePair,
    @SerializedName("exchange_type") override val exchange: String,
    @SerializedName("order_type") val ordersType: TYPE,
    @SerializedName("direction") val direction: DIRECTION,
    orderBalanceType: String = "first", // if first => BTC balance, else second => USDT balance (default = second)
    countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    @SerializedName("parameters") val parameters: Parameters
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange.uppercase(),
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
) {
    class Parameters(
        @SerializedName("trading_range") val tradingRange: TradingRange, // Trading Range:: range of price for orders
        @SerializedName("order_quantity") val orderQuantity: Param, // Order Quantity:: order size
        @SerializedName("order_distance") val orderDistance: Param, // Order Distance:: distance between every order
        @SerializedName("profit_distance") val profitDistance: Param, // Order Distance:: distance between first order and next order
        @SerializedName("order_max_quantity") val orderMaxQuantity: Int = Int.MAX_VALUE
    )
}