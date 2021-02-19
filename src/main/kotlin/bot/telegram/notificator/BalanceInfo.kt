package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.Order
import bot.telegram.notificator.exchanges.clients.TradePair

data class BalanceInfo(
    var orderS: Order? = null,
    var orderB: Order? = null,
    var symbols: TradePair,
    var firstBalance: Double = 0.toDouble(),
    var secondBalance: Double = 0.toDouble(),
    var balanceTrade: Double = 0.toDouble()
)