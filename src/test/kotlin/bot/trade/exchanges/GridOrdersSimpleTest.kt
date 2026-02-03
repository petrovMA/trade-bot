package bot.trade.exchanges

import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import bot.trade.exchanges.clients.TradePair
import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.params.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GridOrdersSimpleTest {

    private fun createSimpleBotSettings(): BotSettingsGrid {
        return BotSettingsGrid(
            name = "test-bot",
            pair = TradePair("BTC", "USDT"),
            exchange = "TEST",
            ordersType = TYPE.LIMIT,
            direction = DIRECTION.LONG,
            countOfDigitsAfterDotForAmount = 8,
            countOfDigitsAfterDotForPrice = 8,
            parameters = BotSettingsGrid.Parameters(
                tradingRange = TradingRange(BigDecimal("0.0100"), BigDecimal("0.0200")),
                orderQuantity = OrderQuantity(BigDecimal("100"), false),
                orderDistance = Param(BigDecimal("0.0010"), false),
                profitDistance = Param(BigDecimal("0.0010"), false)
            )
        )
    }

    @Test
    fun testGridOrdersCreation() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createSimpleBotSettings()

        val gridOrders = GridOrders(currentPrice, botSettings)

        assertNotNull(gridOrders.orders)
        assertTrue(gridOrders.orders.size > 0)
    }

    @Test
    fun testOrderDistanceMethod() {
        val currentPrice = BigDecimal("0.0150")
        val botSettings = createSimpleBotSettings()
        val gridOrders = GridOrders(currentPrice, botSettings)

        val price = BigDecimal("0.0100")
        val percentageParam = Param(BigDecimal("5"), true) // 5%
        val absoluteParam = Param(BigDecimal("0.0010"), false)

        val percentageDistance = GridOrders.orderDistance(price, percentageParam)
        val absoluteDistance = GridOrders.orderDistance(price, absoluteParam)

        assertEquals(BigDecimal("0.00050000"), percentageDistance) // 5% from 0.0100
        assertEquals(BigDecimal("0.0010"), absoluteDistance)
    }
}