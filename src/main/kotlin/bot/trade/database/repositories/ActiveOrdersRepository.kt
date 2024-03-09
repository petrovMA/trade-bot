package bot.trade.database.repositories

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.springframework.data.repository.CrudRepository
import java.math.BigDecimal


interface ActiveOrdersRepository : CrudRepository<ActiveOrder, Long> {
    fun findOrderById(id: Long): ActiveOrder?
    fun deleteByOrderId(orderId: String)
    fun findAllByBotNameAndDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun findAllByDirection(direction: DIRECTION): Iterable<ActiveOrder>
    fun findAllByDirectionAndOrderSide(direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun deleteByDirectionAndOrderSide(direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>

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

    fun findTopByBotNameAndDirectionOrderByPriceDesc(botName: String, direction: DIRECTION): ActiveOrder?
    fun findTopByBotNameAndDirectionOrderByPriceAsc(botName: String, direction: DIRECTION): ActiveOrder?
    fun countByBotNameAndDirectionAndOrderSide(botName: String, direction: DIRECTION, side: SIDE): Long
}