package bot.trade.database.repositories

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.springframework.data.repository.CrudRepository

interface ActiveOrdersRepository : CrudRepository<ActiveOrder, Long> {
    fun findOrderById(id: Long): ActiveOrder?
    fun deleteByOrderId(order: ActiveOrder): ActiveOrder?
    fun findAllByBotName(botName: String): Iterable<ActiveOrder>
    fun findAllByDirection(direction: DIRECTION): Iterable<ActiveOrder>
    fun findAllByDirectionAndOrderSide(direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun deleteByDirectionAndOrderSide(direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
}