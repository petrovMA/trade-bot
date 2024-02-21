package bot.trade.database.repositories

import bot.trade.database.data.entities.ActiveOrder
import org.springframework.data.repository.CrudRepository

interface ActiveOrdersRepository : CrudRepository<ActiveOrder, Long> {
    fun findOrderById(id: Long): ActiveOrder?
    fun deleteByOrderId(order: ActiveOrder): ActiveOrder?
    fun findAllByBotName(botName: String): Iterable<ActiveOrder>
}