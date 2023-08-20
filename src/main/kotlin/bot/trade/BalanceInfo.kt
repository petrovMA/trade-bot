package bot.trade

import bot.trade.exchanges.clients.Order
import bot.trade.exchanges.clients.TradePair
import java.math.BigDecimal

data class BalanceInfo(
    var orderS: Order? = null,
    var orderB: Order? = null,
    var symbols: TradePair,
    var firstBalance: BigDecimal = 0.toBigDecimal(),
    var secondBalance: BigDecimal = 0.toBigDecimal(),
    var balanceTrade: BigDecimal = 0.toBigDecimal()
)