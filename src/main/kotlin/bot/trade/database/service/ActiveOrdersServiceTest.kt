package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.junit.jupiter.api.Assertions
import java.math.BigDecimal


class ActiveOrdersServiceTest {
    fun test(service: ActiveOrdersService) {

        service.deleteByOrderIds("1", "2", "3")

        Assertions.assertEquals(0, service.count("test", DIRECTION.SHORT, SIDE.SELL))

        val order1 = ActiveOrder(
            id = null,
            botName = "test",
            orderId = "1",
            tradePair = "BTC_USDT",
            amount = BigDecimal(0.001),
            orderSide = SIDE.SELL,
            price = BigDecimal(60000),
            stopPrice = BigDecimal(61000),
            lastBorderPrice = BigDecimal(60500),
            direction = DIRECTION.SHORT
        )

        val order2 = ActiveOrder(
            id = null,
            botName = "test",
            orderId = "2",
            tradePair = "BTC_USDT",
            amount = BigDecimal(0.001),
            orderSide = SIDE.SELL,
            price = BigDecimal(60050),
            stopPrice = BigDecimal(61000),
            lastBorderPrice = BigDecimal(60500),
            direction = DIRECTION.SHORT
        )

        val id1 = service.saveOrder(order1).id
        val id2 = service.saveOrder(order2).id

        Assertions.assertEquals(2, service.count("test", DIRECTION.SHORT, SIDE.SELL))
        Assertions.assertEquals(0, service.count("t", DIRECTION.SHORT, SIDE.SELL))
        Assertions.assertEquals(0, service.count("test", DIRECTION.LONG, SIDE.SELL))
        Assertions.assertEquals(0, service.count("test", DIRECTION.SHORT, SIDE.BUY))

        val order1byId = service.getOrderById(id1!!)
        val order2byId = service.getOrderById(id2!!)

        val order1byPrice = service.getOrderWithMinPrice("test", DIRECTION.SHORT)
        val order2byPrice = service.getOrderWithMaxPrice("test", DIRECTION.SHORT)

        Assertions.assertEquals(order1byId, order1byPrice)
        Assertions.assertEquals(order2byId, order2byPrice)

        val order3 = ActiveOrder(
            id = null,
            botName = "test",
            orderId = "3",
            tradePair = "BTC_USDT",
            amount = BigDecimal(0.001),
            orderSide = SIDE.SELL,
            price = BigDecimal(60100),
            stopPrice = BigDecimal(61000),
            lastBorderPrice = BigDecimal(60500),
            direction = DIRECTION.SHORT
        )

        val id3 = service.saveOrder(order3).id

        Assertions.assertEquals(3, service.count("test", DIRECTION.SHORT, SIDE.SELL))

        val order3byPrice = service.getOrderWithMaxPrice("test", DIRECTION.SHORT)
        val order3byId = service.getOrderById(id3!!)

        Assertions.assertNotEquals(order2byId, order3byPrice)
        Assertions.assertEquals(order3byId, order3byPrice)

        val order1NotFound = service.getOrderWithMaxPrice("test", DIRECTION.LONG)
        val order2NotFound = service.getOrderWithMinPrice("test", DIRECTION.LONG)

        Assertions.assertEquals(null, order1NotFound)
        Assertions.assertEquals(null, order2NotFound)
    }
}