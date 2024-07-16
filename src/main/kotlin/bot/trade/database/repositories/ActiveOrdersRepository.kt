package bot.trade.database.repositories

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal
import java.util.*


interface ActiveOrdersRepository : CrudRepository<ActiveOrder, Long> {
    fun findOrderById(id: Long): ActiveOrder?
    fun deleteByOrderId(orderId: String)
    fun findByOrderId(orderId: String): ActiveOrder?
    fun findAllByBotNameAndDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun findByBotNameAndOrderId(botName: String, orderId: String): ActiveOrder?
    fun findAllByBotNameAndDirectionAndOrderSide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun deleteByBotNameAndDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun deleteByBotName(botName: String): Iterable<ActiveOrder>

    fun deleteByBotNameAndDirectionAndOrderSide(
        botName: String,
        direction: DIRECTION,
        side: SIDE
    ): Iterable<ActiveOrder>

    fun findByBotNameAndDirectionAndPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder?

    fun findByBotNameAndDirectionAndPriceGreaterThanAndPriceLessThan(
        botName: String,
        direction: DIRECTION,
        minPrice: BigDecimal,
        maxPrice: BigDecimal
    ): Iterable<ActiveOrder>

    fun findTopByBotNameAndDirectionAndPriceLessThanEqualOrderByPriceDesc(
        botName: String,
        direction: DIRECTION,
        maxPrice: BigDecimal
    ): ActiveOrder?

    fun findTopByBotNameAndDirectionAndPriceGreaterThanEqualOrderByPriceAsc(
        botName: String,
        direction: DIRECTION,
        minPrice: BigDecimal
    ): ActiveOrder?

    fun countByBotNameAndDirectionAndOrderSide(botName: String, direction: DIRECTION, side: SIDE): Long
    fun countByBotName(botName: String): Long
}