package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.database.service.impl.ActiveOrdersServiceImpl
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.*

@DataJpaTest
@ActiveProfiles("test")
class ServiceTest {

    @Autowired
    private lateinit var repository: ActiveOrdersRepository

    @Test
    fun test() {
        val service = ActiveOrdersServiceImpl(repository)

        service.deleteByDirectionAndSide("test", DIRECTION.SHORT, SIDE.SELL)

        Assertions.assertEquals(0, service.count("test", DIRECTION.SHORT, SIDE.SELL))

        val order1 = ActiveOrder(
            id = null,
            botName = "test",
            orderId = UUID.randomUUID().toString(),
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
            orderId = UUID.randomUUID().toString(),
            tradePair = "BTC_USDT",
            amount = BigDecimal(0.001),
            orderSide = SIDE.SELL,
            price = BigDecimal(60050),
            stopPrice = BigDecimal(61000),
            lastBorderPrice = BigDecimal(60500),
            direction = DIRECTION.SHORT
        )

        val id1 = service.saveOrder(order1).id
        service.saveOrder(order2).id

        Assertions.assertEquals(2, service.count("test", DIRECTION.SHORT, SIDE.SELL))
        Assertions.assertEquals(0, service.count("t", DIRECTION.SHORT, SIDE.SELL))
        Assertions.assertEquals(0, service.count("test", DIRECTION.LONG, SIDE.SELL))
        Assertions.assertEquals(0, service.count("test", DIRECTION.SHORT, SIDE.BUY))

        val order1byId = service.getOrderById(id1!!)
        val order2byId = service.getOrderByPrice("test", DIRECTION.SHORT, BigDecimal(60050))

        val order1byPrice = service.getOrderWithMinPrice("test", DIRECTION.SHORT, BigDecimal(0))
        val order2byPrice = service.getOrderWithMaxPrice("test", DIRECTION.SHORT, BigDecimal(999999999999))

        Assertions.assertEquals(order1byId, order1byPrice)
        Assertions.assertEquals(order2byId, order2byPrice)

        val order3 = ActiveOrder(
            id = null,
            botName = "test",
            orderId = UUID.randomUUID().toString(),
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

        val order3byPrice = service.getOrderWithMaxPrice("test", DIRECTION.SHORT, BigDecimal(999999999999))
        val order3byId = service.getOrderById(id3!!)

        Assertions.assertNotEquals(order2byId, order3byPrice)
        Assertions.assertEquals(order3byId, order3byPrice)

        val order1NotFound = service.getOrderWithMaxPrice("test", DIRECTION.LONG, BigDecimal(999999999999))
        val order2NotFound = service.getOrderWithMinPrice("test", DIRECTION.LONG, BigDecimal(0))

        Assertions.assertEquals(null, order1NotFound)
        Assertions.assertEquals(null, order2NotFound)

        val orders = service.getOrderByPriceBetween("test", DIRECTION.SHORT, BigDecimal(59999), BigDecimal(60100))
            .sortedBy { it.price }
            .toList()

        Assertions.assertEquals(listOf(order1byId, order2byId), orders)
    }
}