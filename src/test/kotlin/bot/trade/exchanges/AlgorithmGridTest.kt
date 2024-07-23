package bot.trade.exchanges

import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.exchanges.clients.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal


@DataJpaTest
@ActiveProfiles("test")
class AlgorithmGridTest {

    @Autowired
    private lateinit var repository: ActiveOrdersRepository

    @Test
    fun createOrdersTest() {
        val (algorithmTrader, exchange) = testExchange("algorithmGridTest.json", repository)
            .run { first as AlgorithmGrid to second }

        repository.deleteByBotNameAndDirection(algorithmTrader.botSettings.name, DIRECTION.LONG)

        algorithmTrader.callPrivateFunc("checkOrders", BigDecimal(0.02022))

        val orders = exchange.orders.sortedBy { it.price }

        assertEquals(284, orders.size)
        assertEquals(128, orders.filter { it.side == SIDE.BUY }.size)
        assertEquals(156, orders.filter { it.side == SIDE.SELL }.size)
    }
}