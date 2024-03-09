package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import java.math.BigDecimal

interface ActiveOrdersService {
    fun saveOrder(order: ActiveOrder): ActiveOrder
    fun getOrderById(id: Long): ActiveOrder?
    fun getOrderWithMaxPrice(botName: String, direction: DIRECTION): ActiveOrder?
    fun getOrderWithMinPrice(botName: String, direction: DIRECTION): ActiveOrder?
    fun getOrderByPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder?
    fun getOrderByPriceBetween(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal): Iterable<ActiveOrder>
    fun deleteByDirectionAndSide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun count(botName: String, direction: DIRECTION, side: SIDE): Long
    fun deleteById(id: Long)
    fun deleteByOrderId(orderId: String)
}