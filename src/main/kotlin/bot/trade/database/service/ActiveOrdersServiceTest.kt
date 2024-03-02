package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.junit.jupiter.api.Assertions
import java.math.BigDecimal

class ActiveOrdersServiceTest {
    fun test(service: ActiveOrdersService) {

        val order = ActiveOrder(
            id = null,
            botName = "test",
            orderId = "123",
            tradePair = "BTC_USDT",
            amount = BigDecimal(0.001),
            orderSide = SIDE.SELL,
            price = BigDecimal(60000),
            stopPrice = BigDecimal(61000),
            lastBorderPrice = BigDecimal(60500),
            direction = DIRECTION.SHORT
        )

        val id = service.saveOrder(order).id

        val order2 = service.getOrderById(id!!)

        Assertions.assertEquals(order, order2)
    }
}