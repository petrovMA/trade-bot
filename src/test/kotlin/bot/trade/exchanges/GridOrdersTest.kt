package bot.trade.exchanges

import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import bot.trade.exchanges.clients.TradePair
import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.params.*
import bot.trade.libs.round
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GridOrdersTest {

    private fun createTestBotSettings(
        lowerBound: BigDecimal = BigDecimal("0.0100"),
        upperBound: BigDecimal = BigDecimal("0.0200"),
        orderQuantityValue: BigDecimal = BigDecimal("100"),
        isCounterBalance: Boolean = false,
        orderDistanceValue: BigDecimal = BigDecimal("0.0005"),
        orderDistanceUsePercent: Boolean = false,
        profitDistanceValue: BigDecimal = BigDecimal("0.0010"),
        profitDistanceUsePercent: Boolean = false
    ): BotSettingsGrid {
        return BotSettingsGrid(
            name = "test-grid-bot",
            pair = TradePair("BTC", "USDT"),
            exchange = "TEST",
            ordersType = TYPE.LIMIT,
            direction = DIRECTION.LONG,
            countOfDigitsAfterDotForAmount = 8,
            countOfDigitsAfterDotForPrice = 8,
            parameters = BotSettingsGrid.Parameters(
                tradingRange = TradingRange(lowerBound, upperBound),
                orderQuantity = OrderQuantity(orderQuantityValue, isCounterBalance),
                orderDistance = Param(orderDistanceValue, orderDistanceUsePercent),
                profitDistance = Param(profitDistanceValue, profitDistanceUsePercent)
            )
        )
    }

    @Test
    fun `constructor creates correct number of orders within trading range`() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createTestBotSettings(
            lowerBound = BigDecimal("0.0100"),
            upperBound = BigDecimal("0.0200"),
            orderDistanceValue = BigDecimal("0.0010")
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        assertEquals(10, gridOrders.orders.size)
    }

    @Test
    fun `constructor creates buy orders below current price and sell orders above`() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createTestBotSettings(
            lowerBound = BigDecimal("0.0100"),
            upperBound = BigDecimal("0.0200"),
            orderDistanceValue = BigDecimal("0.0010")
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        val buyOrders = gridOrders.orders.filter { it.orderSide == SIDE.BUY }
        val sellOrders = gridOrders.orders.filter { it.orderSide == SIDE.SELL }

        assertTrue(buyOrders.all { it.price != null && it.price!! <= currentPrice })
        assertTrue(sellOrders.all { it.price != null && it.price!! > currentPrice })
    }

    @Test
    fun `constructor sets correct stop prices for sell orders`() {
        val currentPrice = BigDecimal("0.0150")
        val profitDistance = BigDecimal("0.0010")
        val botSettings = createTestBotSettings(
            profitDistanceValue = profitDistance
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        val sellOrders = gridOrders.orders.filter { it.orderSide == SIDE.SELL }

        sellOrders.forEach { order ->
            val expectedStopPrice = order.price!!.add(profitDistance)
            assertEquals(expectedStopPrice, order.stopPrice)
        }
    }

    @Test
    fun `constructor sets correct order properties`() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createTestBotSettings()

        val gridOrders = GridOrders(currentPrice, botSettings)

        gridOrders.orders.forEach { order ->
            assertEquals("BTCUSDT", order.tradePair)
            assertEquals(DIRECTION.LONG, order.direction)
            assertEquals("test-grid-bot", order.botName)
            assertEquals(currentPrice, order.lastBorderPrice)
        }
    }

    @Test
    fun `calcAmount returns correct amount for counter balance`() {
        val orderQuantity = OrderQuantity(BigDecimal("1000"), true)
        val price = BigDecimal("0.0100")
        val countOfDigits = 2

        val amount = GridOrders.calcAmount(orderQuantity, price, countOfDigits)

        assertEquals(BigDecimal("100000.00"), amount)
    }

    @Test
    fun `calcAmount returns original value for non-counter balance`() {
        val orderQuantity = OrderQuantity(BigDecimal("50"), false)
        val price = BigDecimal("0.0100")
        val countOfDigits = 2

        val amount = GridOrders.calcAmount(orderQuantity, price, countOfDigits)

        assertEquals(BigDecimal("50"), amount)
    }

    @Test
    fun `orderDistance returns absolute value when not percentage`() {
        val gridOrders = GridOrders(BigDecimal("0.0150"), createTestBotSettings())
        val currPrice = BigDecimal("0.0100")
        val orderDistance = Param(BigDecimal("0.0010"), false)

        val distance = GridOrders.orderDistance(currPrice, orderDistance)

        assertEquals(BigDecimal("0.0010"), distance)
    }

    @Test
    fun `constructor handles current price below trading range`() {
        val currentPrice = BigDecimal("0.0050") // Below lower bound
        val botSettings = createTestBotSettings(
            lowerBound = BigDecimal("0.0100"),
            upperBound = BigDecimal("0.0200"),
            orderDistanceValue = BigDecimal("0.0010")
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        val buyOrders = gridOrders.orders.filter { it.orderSide == SIDE.BUY }
        val sellOrders = gridOrders.orders.filter { it.orderSide == SIDE.SELL }

        assertEquals(0, buyOrders.size)
        assertEquals(10, sellOrders.size)
    }

    @Test
    fun `constructor handles current price above trading range`() {
        val currentPrice = BigDecimal("0.0250") // Above upper bound
        val botSettings = createTestBotSettings(
            lowerBound = BigDecimal("0.0100"),
            upperBound = BigDecimal("0.0200"),
            orderDistanceValue = BigDecimal("0.0010")
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        val buyOrders = gridOrders.orders.filter { it.orderSide == SIDE.BUY }
        val sellOrders = gridOrders.orders.filter { it.orderSide == SIDE.SELL }

        assertEquals(10, buyOrders.size)
        assertEquals(0, sellOrders.size)
    }

    @Test
    fun `constructor with percentage-based order distance`() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createTestBotSettings(
            orderDistanceValue = BigDecimal("1"), // 1%
            orderDistanceUsePercent = true
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        assertTrue(gridOrders.orders.isNotEmpty())

        // Verify that orders are created with percentage-based spacing
        val sortedPrices = gridOrders.orders.mapNotNull { it.price }.sorted()
        if (sortedPrices.size >= 2) {
            val firstPrice = sortedPrices[0]
            val secondPrice = sortedPrices[1]
            val expectedDistance = firstPrice.multiply(BigDecimal("0.01"))
            val actualDistance = secondPrice.subtract(firstPrice)

            assertTrue(actualDistance.compareTo(expectedDistance) == 0)
        }
    }

    @Test
    fun `constructor with percentage-based profit distance`() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createTestBotSettings(
            profitDistanceValue = BigDecimal("2"), // 2%
            profitDistanceUsePercent = true
        )

        val gridOrders = GridOrders(currentPrice, botSettings)

        val sellOrders = gridOrders.orders.filter { it.orderSide == SIDE.SELL }

        sellOrders.forEach { order ->
            val expectedProfitDistance = order.price!!.multiply(BigDecimal("0.02"))
            val expectedStopPrice = order.price!!.add(expectedProfitDistance)
            assertEquals(expectedStopPrice.round(), order.stopPrice)
        }
    }
}