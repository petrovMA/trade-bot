package bot.trade.database.repositories

import bot.trade.database.data.entities.NotificationType
import bot.trade.database.data.entities.Order
import org.springframework.data.repository.CrudRepository

interface OrdersRepository : CrudRepository<Order, Long> {
    fun findOrderById(id: Int): Order?
    fun deleteById(order: Order): Order?
    fun findAllByBotName(botName: String): Iterable<Order>
    fun findAllByBotNameAndNotificationType(botName: String, notificationType: NotificationType): Iterable<Order>
}