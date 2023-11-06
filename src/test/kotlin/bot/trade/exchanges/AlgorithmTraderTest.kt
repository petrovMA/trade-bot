package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import bot.trade.libs.div8
import bot.trade.libs.readConf
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import utils.mapper.Mapper
import utils.resourceFile
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque

class AlgorithmTraderTest {

    @Test
    fun testExecuteInOrdersWithMinOrderAmount() {
        val (algorithmTrader, exchange) = testExchange("testExecuteInOrdersWithMinOrderAmountSettings.json")

        algorithmTrader.orders.clear()

        Mapper.asMapObjects<String, Order>(
            "testExecuteInOrdersWithMinOrderAmountOrders.json".file(),
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

        assertOrders("testExecuteInOrdersWithMinOrderAmountOrdersExpected.json".file(), algorithmTrader.orders)
    }

    @Test
    fun testCheckLongStrategy() {
        val (algorithmTrader, exchange) = testExchange("testCheckLongStrategySettings.json")

        val expectedOrder1 = Order(
            orderId = "",
            pair = TradePair("ETH", "USDT"),
            price = "1502.00".toBigDecimal(),
            origQty = 0.14.toBigDecimal(),
            executedQty = 0.toBigDecimal(),
            side = SIDE.BUY,
            type = TYPE.MARKET,
            status = STATUS.NEW
        )

        val expectedOrder2 = Order(
            orderId = "",
            pair = TradePair("ETH", "USDT"),
            price = "1513.00".toBigDecimal(),
            origQty = 0.03.toBigDecimal(),
            executedQty = 0.toBigDecimal(),
            side = SIDE.SELL,
            type = TYPE.MARKET,
            status = STATUS.NEW
        )

        val expectedOrder3 = Order(
            orderId = "",
            pair = TradePair("ETH", "USDT"),
            price = "1511.00".toBigDecimal(),
            origQty = 0.05.toBigDecimal(),
            executedQty = 0.toBigDecimal(),
            side = SIDE.SELL,
            type = TYPE.MARKET,
            status = STATUS.NEW
        )

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

        assertOrders(listOf(expectedOrder1), exchange.orders)

        algorithmTrader.handle(Trade(1503.toBigDecimal(), 1.toBigDecimal(), 11))
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 12))
        algorithmTrader.handle(Trade(1509.toBigDecimal(), 1.toBigDecimal(), 13))
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 14))
        algorithmTrader.handle(Trade(1515.toBigDecimal(), 1.toBigDecimal(), 15))
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 16))
        algorithmTrader.handle(Trade(1513.toBigDecimal(), 1.toBigDecimal(), 17))

        assertOrders(listOf(expectedOrder1, expectedOrder2), exchange.orders)

        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 17))

        assertOrders(listOf(expectedOrder1, expectedOrder2), exchange.orders)

        algorithmTrader.handle(Trade(1511.toBigDecimal(), 1.toBigDecimal(), 18))

        assertOrders(listOf(expectedOrder1, expectedOrder2, expectedOrder3), exchange.orders)

        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 19))

        assertOrders(listOf(expectedOrder1, expectedOrder2, expectedOrder3), exchange.orders)

        assertOrders("testCheckLongStrategyOrdersExpected.json".file(), algorithmTrader.orders)
    }

    @Test
    fun testCheckShortStrategy() {
        val (algorithmTrader, exchange) = testExchange("testCheckShortStrategy/testCheckShortStrategySettings.json")

        val expectedOrder1 = Order(
            orderId = "",
            pair = TradePair("ETH", "USDT"),
            price = "1533.50".toBigDecimal(),
            origQty = 0.16.toBigDecimal(),
            executedQty = 0.toBigDecimal(),
            side = SIDE.SELL,
            type = TYPE.MARKET,
            status = STATUS.NEW
        )

        algorithmTrader.orders.clear()

        var number = 0L
        var price = BigDecimal(0)

        for (i in 15840 downTo 14400 step 5) {
            number++
            price = BigDecimal(i).div8(BigDecimal(10))
            algorithmTrader.handle(Trade(price, BigDecimal(1), number))

            if (i in 15800..15840) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_1.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15700 until 15800) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_2.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15600 until 15700) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_3.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15500 until 15600) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_4.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15400 until 15500) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_5.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15300 until 15400) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_6.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15200 until 15300) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_7.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15100 until 15200) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_8.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15000 until 15100) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_9.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14900 until 15000) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_10.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14800 until 14900) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_11.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14700 until 14800) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_12.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14600 until 14700) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_13.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14500 until 14600) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_14.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14400 until 14500) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_15.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
        }

        for (i in 14400..15385 step 5) {
            number++
            price = BigDecimal(i).div8(BigDecimal(10))
            algorithmTrader.handle(Trade(price, BigDecimal(1), number))

            if (i in 14400 until 14500) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_15.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
        }

        assertOrders(
            "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_16.json".file(),
            algorithmTrader.orders,
            "Assertion failed on price = ${price.toPrice()}, "
        )

        for (i in 15385 downTo 14240 step 5) {
            number++
            price = BigDecimal(i).div8(BigDecimal(10))
            algorithmTrader.handle(Trade(price, BigDecimal(1), number))

            if (i in 15335 startExclusive 15385) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_16.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }

            if (i in 14579..14581) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_17.json".file(),
                    algorithmTrader.orders,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
        }

        assertOrders(listOf(expectedOrder1), exchange.orders)

        assertOrders(
            "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_18.json".file(),
            algorithmTrader.orders,
            "Assertion failed on price = ${price.toPrice()}, "
        )
    }

    private fun testExchange(settingsFile: String) = ClientTestExchange().let { exchange ->
        AlgorithmTrader(
            botSettings = Mapper.asObject<BotSettingsTrader>(settingsFile.file().readText()),
            exchangeBotsFiles = "",
            queue = LinkedBlockingDeque<CommonExchangeData>(),
            exchangeEnum = ExchangeEnum.TEST,
            conf = readConf("TEST.conf".file().path)!!,
            api = "",
            sec = "",
            client = exchange,
            isLog = false,
            isEmulate = true,
        ) { _, _ -> } to exchange
    }

    fun String.file() = resourceFile<AlgorithmTraderTest>(this)

    infix fun Int.startExclusive(other: Int): IntRange = IntRange(this + 1, other)

    fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)
}