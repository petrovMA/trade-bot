package bot.telegram.notificator.database.repositories

import bot.telegram.notificator.database.data.entities.NotificationType
import bot.telegram.notificator.database.data.entities.Order
import org.springframework.data.repository.CrudRepository

interface OrdersRepository : CrudRepository<Order, Long> {
    fun findOrderById(id: Int): Order?
    fun deleteById(order: Order): Order?
    fun findAllByBotNameAndNotificationType(botName: String, notificationType: NotificationType): Iterable<Order>
}