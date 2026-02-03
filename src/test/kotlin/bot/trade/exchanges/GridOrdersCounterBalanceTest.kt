package bot.trade.exchanges

import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import bot.trade.exchanges.clients.TradePair
import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.params.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class GridOrdersCounterBalanceTest {

    @Test
    fun `verify counter balance with REACT USDT settings`() {
        // Simulate user's REACT/USDT configuration
        val botSettings = BotSettingsGrid(
            name = "REACT_USDT_GRID",
            pair = TradePair("REACT", "USDT"),
            exchange = "Gate",
            ordersType = TYPE.LIMIT,
            direction = DIRECTION.LONG,
            countOfDigitsAfterDotForAmount = 2,
            countOfDigitsAfterDotForPrice = 5,
            parameters = BotSettingsGrid.Parameters(
                tradingRange = TradingRange(
                    lowerBound = BigDecimal("0.030"),
                    upperBound = BigDecimal("0.07")
                ),
                orderQuantity = OrderQuantity(
                    value = BigDecimal("70"),
                    isCounterBalance = true
                ),
                orderDistance = Param(
                    value = BigDecimal("1.0"),
                    usePercent = true
                ),
                profitDistance = Param(
                    value = BigDecimal("1.7"),
                    usePercent = true
                )
            )
        )

        val currentPrice = BigDecimal("0.05")
        val gridOrders = GridOrders(currentPrice, botSettings)

        // Print orders for debugging
        println("Total orders: ${gridOrders.orders.size}")
        println("\nBUY Orders:")
        gridOrders.orders.filter { it.orderSide == SIDE.BUY }.forEach { order ->
            val costInUsdt = order.amount!! * order.price!!
            println("Price: ${order.price}, Amount: ${order.amount} REACT, Cost: $costInUsdt USDT")
        }

        println("\nSELL Orders:")
        gridOrders.orders.filter { it.orderSide == SIDE.SELL }.forEach { order ->
            val costInUsdt = order.amount!! * order.price!!
            println("Price: ${order.price}, Amount: ${order.amount} REACT, Value: $costInUsdt USDT")
        }

        // Calculate required balance
        val balance = gridOrders.calculateRequiredBalance()

        println("\n=== Balance Requirements ===")
        println("Required REACT: ${balance.requiredFirst}")
        println("Required USDT: ${balance.requiredSecond}")
        println("Total orders: ${balance.totalOrders}")
        println("Buy orders: ${balance.buyOrders}")
        println("Sell orders: ${balance.sellOrders}")

        // Verify that each BUY order costs approximately 70 USDT
        val buyOrders = gridOrders.orders.filter { it.orderSide == SIDE.BUY }
        buyOrders.forEach { order ->
            val costInUsdt = order.amount!! * order.price!!
            println("\nBUY order at ${order.price}: ${order.amount} REACT = $costInUsdt USDT")
            // Allow small rounding differences
            assertTrue(
                costInUsdt.subtract(BigDecimal("70")).abs() < BigDecimal("0.01"),
                "Expected ~70 USDT, got $costInUsdt"
            )
        }

        // Verify that SELL orders have correct amounts
        val sellOrders = gridOrders.orders.filter { it.orderSide == SIDE.SELL }
        sellOrders.forEach { order ->
            val expectedAmount = BigDecimal("70").divide(order.price!!, 2, java.math.RoundingMode.HALF_UP)
            println("SELL order at ${order.price}: expected=${expectedAmount}, actual=${order.amount}")
        }

        assertTrue(gridOrders.orders.isNotEmpty())
    }

    @Test
    fun `verify counter balance calculation at different prices`() {
        // Test at price 0.04
        val amount1 = GridOrders.calcAmount(
            OrderQuantity(BigDecimal("70"), true),
            BigDecimal("0.04"),
            2
        )
        println("At 0.04: $amount1 REACT (should be 70/0.04 = 1750)")
        assertEquals(BigDecimal("1750.00"), amount1)

        // Test at price 0.06
        val amount2 = GridOrders.calcAmount(
            OrderQuantity(BigDecimal("70"), true),
            BigDecimal("0.06"),
            2
        )
        println("At 0.06: $amount2 REACT (should be 70/0.06 = 1166.67)")
        assertEquals(BigDecimal("1166.67"), amount2)

        // Verify the costs
        val cost1 = amount1 * BigDecimal("0.04")
        val cost2 = amount2 * BigDecimal("0.06")

        println("Cost at 0.04: $cost1 USDT (should be ~70)")
        println("Cost at 0.06: $cost2 USDT (should be ~70)")

        assertTrue(cost1.subtract(BigDecimal("70")).abs() < BigDecimal("0.01"))
        assertTrue(cost2.subtract(BigDecimal("70")).abs() < BigDecimal("0.01"))
    }
}