package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import bot.trade.libs.div8
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import utils.mapper.Mapper
import java.math.BigDecimal

class AlgorithmTraderTest {

    private fun Trade.toKline() = Candlestick(
        openTime = time,
        closeTime = time + 1000000,
        open = 0.toBigDecimal(),
        high = 0.toBigDecimal(),
        low = 0.toBigDecimal(),
        close = price,
        volume = 0.toBigDecimal()
    )

    @Test
    fun testExecuteInOrdersWithMinOrderAmount() {
        val (algorithmTrader, exchange) = testExchange("testExecuteInOrdersWithMinOrderAmountSettings.json")

        algorithmTrader.orders().second.clear()

        Mapper.asMapObjects<String, Order>(
            "testExecuteInOrdersWithMinOrderAmountOrders.json".file(),
            object : TypeToken<Map<String?, Order?>?>() {}.type
        ).forEach { (k, v) -> algorithmTrader.orders().second[k] = v }

        algorithmTrader.handle(Trade(1518.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1516.toBigDecimal(), 1.toBigDecimal(), 1).toKline())
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 2).toKline())
        algorithmTrader.handle(Trade(1513.toBigDecimal(), 1.toBigDecimal(), 3).toKline())
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 4).toKline())

        assertOrders(listOf(), exchange.orders)

        algorithmTrader.handle(Trade(1500.toBigDecimal(), 1.toBigDecimal(), 5).toKline())
        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 4).toKline())
        algorithmTrader.handle(Trade(1508.toBigDecimal(), 1.toBigDecimal(), 6).toKline())
        algorithmTrader.handle(Trade(1507.toBigDecimal(), 1.toBigDecimal(), 7).toKline())
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 8).toKline())
        algorithmTrader.handle(Trade(1505.toBigDecimal(), 1.toBigDecimal(), 9).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1510.00".toBigDecimal(),
                    origQty = 0.016.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        assertOrders("testExecuteInOrdersWithMinOrderAmountOrdersExpected.json".file(), algorithmTrader.orders().second)
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

        algorithmTrader.orders().second.clear()

        algorithmTrader.handle(Trade(1516.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 1).toKline())
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 2).toKline())
        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 3).toKline())
        algorithmTrader.handle(Trade(1508.toBigDecimal(), 1.toBigDecimal(), 4).toKline())
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 5).toKline())
        algorithmTrader.handle(Trade(1504.toBigDecimal(), 1.toBigDecimal(), 6).toKline())
        algorithmTrader.handle(Trade(1502.toBigDecimal(), 1.toBigDecimal(), 7).toKline())
        algorithmTrader.handle(Trade(1500.toBigDecimal(), 1.toBigDecimal(), 8).toKline())
        algorithmTrader.handle(Trade(1501.toBigDecimal(), 1.toBigDecimal(), 9).toKline())

        assertOrders(listOf(), exchange.orders)

        algorithmTrader.handle(Trade(1502.toBigDecimal(), 1.toBigDecimal(), 10).toKline())

        assertOrders(listOf(expectedOrder1), exchange.orders)

        algorithmTrader.handle(Trade(1503.toBigDecimal(), 1.toBigDecimal(), 11).toKline())
        algorithmTrader.handle(Trade(1506.toBigDecimal(), 1.toBigDecimal(), 12).toKline())
        algorithmTrader.handle(Trade(1509.toBigDecimal(), 1.toBigDecimal(), 13).toKline())
        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 14).toKline())
        algorithmTrader.handle(Trade(1515.toBigDecimal(), 1.toBigDecimal(), 15).toKline())
        algorithmTrader.handle(Trade(1514.toBigDecimal(), 1.toBigDecimal(), 16).toKline())
        algorithmTrader.handle(Trade(1513.toBigDecimal(), 1.toBigDecimal(), 17).toKline())

        assertOrders(listOf(expectedOrder1, expectedOrder2), exchange.orders)

        algorithmTrader.handle(Trade(1512.toBigDecimal(), 1.toBigDecimal(), 17).toKline())

        assertOrders(listOf(expectedOrder1, expectedOrder2), exchange.orders)

        algorithmTrader.handle(Trade(1511.toBigDecimal(), 1.toBigDecimal(), 18).toKline())

        assertOrders(listOf(expectedOrder1, expectedOrder2, expectedOrder3), exchange.orders)

        algorithmTrader.handle(Trade(1510.toBigDecimal(), 1.toBigDecimal(), 19).toKline())

        assertOrders(listOf(expectedOrder1, expectedOrder2, expectedOrder3), exchange.orders)

        assertOrders("testCheckLongStrategyOrdersExpected.json".file(), algorithmTrader.orders().second)
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

        algorithmTrader.orders().third.clear()

        var number = 0L
        var price = BigDecimal(0)

        for (i in 15900 downTo 14400 step 5) {
            number++
            price = BigDecimal(i).div8(BigDecimal(10))
            algorithmTrader.handle(Trade(price, BigDecimal(1), number).toKline())

            if (i in 15805..15840) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_1.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15705 until 15800) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_2.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15605 until 15700) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_3.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15505 until 15600) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_4.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15405 until 15500) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_5.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15305 until 15400) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_6.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15205 until 15300) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_7.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15105 until 15200) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_8.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 15005 until 15100) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_9.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14905 until 15000) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_10.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14805 until 14900) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_11.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14705 until 14800) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_12.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14605 until 14700) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_13.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14505 until 14600) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_14.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
            if (i in 14405 until 14500) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_15.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
        }

        for (i in 14400..15385 step 5) {
            number++
            price = BigDecimal(i).div8(BigDecimal(10))
            algorithmTrader.handle(Trade(price, BigDecimal(1), number).toKline())

            if (i in 14495 until 14500) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_16.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
        }

        assertOrders(
            "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_17.json".file(),
            algorithmTrader.orders().third,
            "Assertion failed on price = ${price.toPrice()}, "
        )

        for (i in 15385 downTo 14240 step 5) {
            number++
            price = BigDecimal(i).div8(BigDecimal(10))
            algorithmTrader.handle(Trade(price, BigDecimal(1), number).toKline())

            if (i in 15335 startExclusive 15385) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_17.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }

            if (i in 14579..14581) {
                assertOrders(
                    "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_18.json".file(),
                    algorithmTrader.orders().third,
                    "Assertion failed on price = ${price.toPrice()}, "
                )
            }
        }

        assertOrders(listOf(expectedOrder1), exchange.orders)

        assertOrders(
            "testCheckShortStrategy/testCheckShortStrategyOrdersExpected_19.json".file(),
            algorithmTrader.orders().third,
            "Assertion failed on price = ${price.toPrice()}, "
        )
    }

    @Test
    fun testInOrdersWithPercentInOrderDistance() {
        val (algorithmTrader, exchange) = testExchange("testExecuteInOrdersWithPercentOrderDistanceSettings.json")

        algorithmTrader.handle(Trade(1500.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1499.toBigDecimal(), 1.toBigDecimal(), 1).toKline())

        assertOrders(listOf(), exchange.orders)

        algorithmTrader.handle(Trade(1485.toBigDecimal(), 1.toBigDecimal(), 2).toKline())
        algorithmTrader.handle(Trade(1590.toBigDecimal(), 1.toBigDecimal(), 3).toKline())

        algorithmTrader.handle(Trade(1588.toBigDecimal(), 1.toBigDecimal(), 4).toKline())
        algorithmTrader.handle(Trade(1587.toBigDecimal(), 1.toBigDecimal(), 5).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "1587.00".toBigDecimal(),
                    origQty = 0.007.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1586.toBigDecimal(), 1.toBigDecimal(), 6).toKline())

        assertOrders("testExecuteInOrdersWithPercentOrderDistanceOrders.json".file(), algorithmTrader.orders().third)
    }
}