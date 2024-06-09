package bot.trade.exchanges.emulate

import bot.trade.exchanges.clients.SIDE
import bot.trade.exchanges.clients.STATUS
import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.clients.TradePair
import java.math.BigDecimal

data class TestBalance(
    var sellCount: Int = 0,
    var boyCount: Int = 0,
    var orderS: TestOrder? = null,
    var orderB: TestOrder? = null,
    var tradePair: TradePair,
    var firstBalance: BigDecimal = 0.toBigDecimal(),
    var feesSum: BigDecimal = 0.toBigDecimal(),
    var tradeVolume: BigDecimal = 0.toBigDecimal(),
    var ordersAmount: Int = 0,
    var maxLongOpenOrdersAmount: Int = 0,
    var maxShortOpenOrdersAmount: Int = 0,
    var secondBalance: BigDecimal = 0.toBigDecimal(),
    var balanceTrade: BigDecimal = 0.toBigDecimal(),
    var correctAverageHigh: BigDecimal = 0.toBigDecimal(),
    var correctAverageLow: BigDecimal = 0.toBigDecimal(),
    var previousCountSell: BigDecimal = 0.toBigDecimal(),
    var previousCountBuy: BigDecimal = 0.toBigDecimal(),
    var previousPriceSell: BigDecimal = 0.toBigDecimal(),
    var previousPriceBuy: BigDecimal = 0.toBigDecimal(),
    var timeStepFive: Long = java.lang.Long.MIN_VALUE,
    var lastSellTime: Long = java.lang.Long.MIN_VALUE,
    var lastBoyTime: Long = java.lang.Long.MIN_VALUE,
    var line1: String = "",
    var line2: String = ""
) {
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
}
