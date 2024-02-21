package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder

interface ActiveOrdersService {
    fun saveOrder(order: ActiveOrder): ActiveOrder
    fun getOrderById(id: Long): ActiveOrder?
    fun deleteById(id: Long)
}