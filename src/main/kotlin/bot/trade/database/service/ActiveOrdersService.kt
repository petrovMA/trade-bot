package bot.trade.database.service

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import java.math.BigDecimal
import java.util.*

interface ActiveOrdersService {
    fun saveOrder(order: ActiveOrder): ActiveOrder
    fun getOrderById(id: Long): ActiveOrder?
    fun getOrderByOrderId(botName: String, orderId: UUID): ActiveOrder?
    fun getOrders(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun getOrderWithMaxPrice(botName: String, direction: DIRECTION, side: SIDE): ActiveOrder?
    fun getOrderWithMinPrice(botName: String, direction: DIRECTION, side: SIDE): ActiveOrder?
    fun getOrdersBySide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun getOrderByPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder?
    fun getOrderByPriceBetween(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal): Iterable<ActiveOrder>
    fun deleteByDirectionAndSide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder>
    fun deleteByDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder>
    fun count(botName: String, direction: DIRECTION, side: SIDE): Long
    fun deleteById(id: Long)
    fun deleteByOrderId(orderId: UUID)
}