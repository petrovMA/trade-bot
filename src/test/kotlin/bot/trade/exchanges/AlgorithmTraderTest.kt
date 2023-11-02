package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import utils.mapper.Mapper
import utils.resourceFile
import java.util.concurrent.LinkedBlockingDeque

class AlgorithmTraderTest {

    @Test
    fun testExecuteInOrdersWithMinOrderAmount() {
        val exchange = ClientTestExchange()

        val algorithmTrader = AlgorithmTrader(
            botSettings = Mapper.asObject<BotSettingsTrader>(resourceFile<AlgorithmTraderTest>("testExecuteInOrdersWithMinOrderAmountSettings.json").readText()),
            exchangeBotsFiles = "",
            queue = LinkedBlockingDeque<CommonExchangeData>(),
            exchangeEnum = ExchangeEnum.TEST,
            conf = readConf(resourceFile<AlgorithmTraderTest>("TEST.conf").path)!!,
            api = "",
            sec = "",
            client = exchange,
            isLog = false,
            isEmulate = true,
        ) { _, _ -> }

        if (algorithmTrader.client !is ClientTestExchange)
            fail("algorithmTrader.client is Not ClientTestExchange")

        algorithmTrader.orders.clear()

        Mapper.asMapObjects<String, Order>(
            resourceFile<AlgorithmTraderTest>("testExecuteInOrdersWithMinOrderAmountOrders.json"),
            object : TypeToken<Map<String?, Order?>?>() {}.type
        ).forEach { (k, v) -> algorithmTrader.orders[k] = v }

        algorithmTrader.handle(Trade(1518.toBigDecimal(), 1.toBigDecimal(), 0))
        algorithmTrader.handle(Trade(1516.toBigDecimal(), 1.toBigDecimal(), 1))
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 2))
        algorithmTrader.handle(Trade(1513.toBigDecimal(), 1.toBigDecimal(), 3))
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 4))

        assertOrders(listOf(), exchange.orders)

        algorithmTrader.handle(Trade(1500.toBigDecimal(), 1.toBigDecimal(), 5))
        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 4))
        algorithmTrader.handle(Trade(1508.toBigDecimal(), 1.toBigDecimal(), 6))
        algorithmTrader.handle(Trade(1507.toBigDecimal(), 1.toBigDecimal(), 7))
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 8))
        algorithmTrader.handle(Trade(1505.toBigDecimal(), 1.toBigDecimal(), 9))

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1510.00".toBigDecimal(),
                    origQty = 0.02.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        assertOrders(
            resourceFile<AlgorithmTraderTest>("testExecuteInOrdersWithMinOrderAmountOrdersExpected.json"),
            algorithmTrader.orders
        )
    }

    @Test
    fun testCheckLongStrategy() {
        val exchange = ClientTestExchange()

        val algorithmTrader = AlgorithmTrader(
            botSettings = Mapper.asObject<BotSettingsTrader>(resourceFile<AlgorithmTraderTest>("testCheckLongStrategySettings.json").readText()),
            exchangeBotsFiles = "",
            queue = LinkedBlockingDeque<CommonExchangeData>(),
            exchangeEnum = ExchangeEnum.TEST,
            conf = readConf(resourceFile<AlgorithmTraderTest>("TEST.conf").path)!!,
            api = "",
            sec = "",
            client = exchange,
            isLog = false,
            isEmulate = true,
        ) { _, _ -> }

        if (algorithmTrader.client !is ClientTestExchange)
            fail("algorithmTrader.client is Not ClientTestExchange")

        algorithmTrader.orders.clear()

        algorithmTrader.handle(Trade(1516.toBigDecimal(), 1.toBigDecimal(), 0))
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 1))
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 2))
        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 3))
        algorithmTrader.handle(Trade(1508.toBigDecimal(), 1.toBigDecimal(), 4))
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 5))
        algorithmTrader.handle(Trade(1504.toBigDecimal(), 1.toBigDecimal(), 6))
        algorithmTrader.handle(Trade(1502.toBigDecimal(), 1.toBigDecimal(), 7))
        algorithmTrader.handle(Trade(1500.toBigDecimal(), 1.toBigDecimal(), 8))
        algorithmTrader.handle(Trade(1501.toBigDecimal(), 1.toBigDecimal(), 9))

        assertOrders(listOf(), exchange.orders)

        algorithmTrader.handle(Trade(1502.toBigDecimal(), 1.toBigDecimal(), 10))

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1502.00".toBigDecimal(),
                    origQty = 0.14.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1503.toBigDecimal(), 1.toBigDecimal(), 11))
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 12))
        algorithmTrader.handle(Trade(1509.toBigDecimal(), 1.toBigDecimal(), 13))
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 14))
        algorithmTrader.handle(Trade(1515.toBigDecimal(), 1.toBigDecimal(), 15))
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 16))
        algorithmTrader.handle(Trade(1513.toBigDecimal(), 1.toBigDecimal(), 17))

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1502.00".toBigDecimal(),
                    origQty = 0.14.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1513.00".toBigDecimal(),
                    origQty = 0.03.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 17))

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1502.00".toBigDecimal(),
                    origQty = 0.14.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1513.00".toBigDecimal(),
                    origQty = 0.03.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1511.toBigDecimal(), 1.toBigDecimal(), 18))

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1502.00".toBigDecimal(),
                    origQty = 0.14.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1513.00".toBigDecimal(),
                    origQty = 0.03.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1511.00".toBigDecimal(),
                    origQty = 0.04.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 19))

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1502.00".toBigDecimal(),
                    origQty = 0.14.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1513.00".toBigDecimal(),
                    origQty = 0.03.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1511.00".toBigDecimal(),
                    origQty = 0.04.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        assertOrders(
            resourceFile<AlgorithmTraderTest>("testCheckLongStrategyOrdersExpected.json"),
            algorithmTrader.orders
        )
    }
}