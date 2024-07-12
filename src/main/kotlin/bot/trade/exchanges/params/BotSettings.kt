package bot.trade.exchanges.params

import bot.trade.exchanges.clients.TradePair

abstract class BotSettings(
    val type: String = "",
    open val name: String,
    open val pair: TradePair,
    open val exchange: String,
    val orderBalanceType: String,
    val countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    val countOfDigitsAfterDotForPrice: Int // number of characters after the dot for price
)