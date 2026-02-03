package bot.trade.database.service

import bot.trade.database.data.entities.NotificationType
import bot.trade.database.data.entities.Order

interface OrderService {
    fun saveOrder(order: Order): Order
    fun getOrderById(id: Int): Order?
    fun deleteById(id: Long): Order?
    fun getAllOrdersByBotName(botName: String): Iterable<Order>?
    fun getAllOrdersByBotNameAndNotificationType(botName: String, notificationType: NotificationType): Iterable<Order>?
}