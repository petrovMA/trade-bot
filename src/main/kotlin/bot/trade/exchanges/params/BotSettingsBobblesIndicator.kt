package bot.trade.exchanges.params

import bot.trade.exchanges.clients.TradePair
import java.math.BigDecimal

class BotSettingsBobblesIndicator(
    name: String,
    pair: TradePair,
    exchange: String,
    orderBalanceType: String,
    countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    val feePercent: BigDecimal = BigDecimal(0.1), // fee for calc profit
    val minOrderSize: BigDecimal, // min order size
    val buyAmountMultiplication: BigDecimal, // buy order size Multiplication
    val sellAmountMultiplication: BigDecimal, // sell order size Multiplication
    val maxShortPosition: BigDecimal = BigDecimal(0.0) // max available short position
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange,
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
)