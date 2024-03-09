package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE

interface ActiveOrdersService {
    fun saveOrder(order: ActiveOrder): ActiveOrder
    fun getOrderById(id: Long): ActiveOrder?
    fun getOrderWithMaxPrice(botName: String, direction: DIRECTION): ActiveOrder?
    fun getOrderWithMinPrice(botName: String, direction: DIRECTION): ActiveOrder?
    fun count(botName: String, direction: DIRECTION, side: SIDE): Long
    fun deleteById(id: Long)
    fun deleteByOrderId(orderId: String)
    fun deleteByOrderIds(vararg orderIds: String)
}