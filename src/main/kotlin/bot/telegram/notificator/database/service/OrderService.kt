package bot.telegram.notificator.database.service

import bot.telegram.notificator.database.data.entities.NotificationType
import bot.telegram.notificator.database.data.entities.Order

interface OrderService {
    fun saveOrder(order: Order): Order
    fun getOrderById(id: Int): Order?

    fun deleteById(id: Long): Order?

    fun getAllOrdersByBotNameAndNotificationType(botName: String, notificationType: NotificationType): Iterable<Order>?
}