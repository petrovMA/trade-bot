package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.clients.SIDE
import bot.telegram.notificator.exchanges.clients.STATUS
import bot.telegram.notificator.exchanges.clients.TYPE
import bot.telegram.notificator.exchanges.clients.TradePair

data class TestBalance(
    var sellCount: Int = 0,
    var boyCount: Int = 0,
    var orderS: TestOrder? = null,
    var orderB: TestOrder? = null,
    var tradePair: TradePair,
    var firstBalance: Double = 0.toDouble(),
    var secondBalance: Double = 0.toDouble(),
    var balanceTrade: Double = 0.toDouble(),
    var correctAverageHigh: Double = 0.0,
    var correctAverageLow: Double = 0.0,
    var previousCountSell: Double = 0.toDouble(),
    var previousCountBuy: Double = 0.toDouble(),
    var previousPriceSell: Double = 0.toDouble(),
    var previousPriceBuy: Double = 0.toDouble(),
    var timeStepFive: Long = java.lang.Long.MIN_VALUE,
    var lastSellTime: Long = java.lang.Long.MIN_VALUE,
    var lastBoyTime: Long = java.lang.Long.MIN_VALUE,
    var line1: String = "",
    var line2: String = ""
)

data class TestOrder(
    var pair: String? = null,
    var orderId: Long? = null,
    var clientOrderId: Long? = null,
    var price: Double? = null,
    var origQty: Double? = null,
    var executedQty: Double? = null,
    var type: TYPE? = null,
    var status: STATUS? = null,
    var side: SIDE? = null
)
