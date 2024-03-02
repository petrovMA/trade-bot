package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.service.impl.ActiveOrdersServiceImpl
import bot.trade.exchanges.assertOrders
import org.junit.jupiter.api.Assertions.assertEquals
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import javax.ws.rs.core.Application


//@RunWith(SpringRunner::class)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = [Application::class])
//@AutoConfigureMockMvc
//@TestPropertySource(locations = ["classpath:application-integrationtest.properties"])

//@SpringBootTest
class ActiveOrdersServiceTest @Autowired constructor(
    val service: ActiveOrdersService
) {

    //    @Test
    fun test() {

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

        val id = service?.saveOrder(order)?.id

        val order2 = service?.getOrderById(id!!)

        assertEquals(order, order2)
    }
}