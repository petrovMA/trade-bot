package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import java.math.BigDecimal
import java.util.*

interface ActiveOrdersService {
    fun saveOrder(order: ActiveOrder): ActiveOrder
    fun updateOrder(order: ActiveOrder): ActiveOrder
    fun getOrderById(id: Long): ActiveOrder?
    fun getOrderByOrderId(botName: String, orderId: String): ActiveOrder?
    fun getOrderByOrderId(orderId: String): ActiveOrder?
    fun getOrders(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun getOrderWithMaxPrice(botName: String, direction: DIRECTION, maxPrice: BigDecimal): ActiveOrder?
    fun getOrderWithMinPrice(botName: String, direction: DIRECTION, minPrice: BigDecimal): ActiveOrder?
    fun getOrdersBySide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun getOrderByPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder?
    fun getOrderByPriceBetween(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal): Iterable<ActiveOrder>
    fun deleteByDirectionAndSide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun deleteByDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun deleteByBotName(botName: String): Iterable<ActiveOrder>
    fun count(botName: String, direction: DIRECTION, side: SIDE): Long
    fun count(botName: String): Long
    fun deleteById(id: Long)
    fun deleteByOrderId(orderId: String)
}