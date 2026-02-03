package bot.trade.balance


import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.service.ActiveOrdersService
import bot.trade.exchanges.clients.*
import java.math.BigDecimal

/**
 * Stub implementations for balance calculation without real exchange/database interactions
 */
class StubActiveOrdersService : ActiveOrdersService {
    private val orders = mutableListOf<ActiveOrder>()
    
    override fun saveOrder(order: ActiveOrder): ActiveOrder {
        orders.add(order)
        return order
    }
    
    override fun updateOrder(order: ActiveOrder): ActiveOrder {
        val index = orders.indexOfFirst { it.id == order.id }
        if (index >= 0) {
            orders[index] = order
        }
        return order
    }
    
    override fun getOrderById(id: Long): ActiveOrder? {
        return orders.find { it.id == id }
    }
    
    override fun getOrderByOrderId(botName: String, orderId: String): ActiveOrder? {
        return orders.find { it.botName == botName && it.orderId == orderId }
    }
    
    override fun getOrderByOrderId(orderId: String): ActiveOrder? {
        return orders.find { it.orderId == orderId }
    }
    
    override fun getOrders(botName: String, direction: DIRECTION): Iterable<ActiveOrder> {
        return orders.filter { it.botName == botName && it.direction == direction }
    }
    
    override fun getOrdersByPair(botName: String, tradePair: String): Iterable<ActiveOrder> {
        return orders.filter { it.botName == botName && it.tradePair == tradePair }
    }
    
    override fun getOrderWithMaxPrice(botName: String, direction: DIRECTION, maxPrice: BigDecimal): ActiveOrder? {
        return orders.filter { it.botName == botName && it.direction == direction && it.price != null && it.price <= maxPrice }
            .maxByOrNull { it.price!! }
    }
    
    override fun getOrderWithMinPrice(botName: String, direction: DIRECTION, minPrice: BigDecimal): ActiveOrder? {
        return orders.filter { it.botName == botName && it.direction == direction && it.price != null && it.price >= minPrice }
            .minByOrNull { it.price!! }
    }
    
    override fun getOrdersWithMaxPriceBySide(botName: String, side: SIDE, maxPrice: BigDecimal): Iterable<ActiveOrder> {
        return orders.filter { it.botName == botName && it.orderSide == side && it.price != null && it.price <= maxPrice }
    }
    
    override fun getOrdersWithMinPriceBySide(botName: String, side: SIDE, minPrice: BigDecimal): Iterable<ActiveOrder> {
        return orders.filter { it.botName == botName && it.orderSide == side && it.price != null && it.price >= minPrice }
    }
    
    override fun getOrdersBySide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder> {
        return orders.filter { it.botName == botName && it.direction == direction && it.orderSide == side }
    }
    
    override fun getOrderByPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder? {
        return orders.find { it.botName == botName && it.direction == direction && it.price == price }
    }
    
    override fun getOrderByPriceBetween(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal): Iterable<ActiveOrder> {
        return orders.filter {
            it.botName == botName &&
            it.direction == direction &&
            it.price != null &&
            it.price >= minPrice &&
            it.price <= maxPrice
        }
    }
    
    override fun getTopOrderByPriceBetweenIncludeMaxPrice(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal): ActiveOrder? {
        return orders.filter {
            it.botName == botName &&
            it.direction == direction &&
            it.price != null &&
            it.price >= minPrice &&
            it.price <= maxPrice
        }.maxByOrNull { it.price!! }
    }
    
    override fun getTopOrderByPriceBetweenIncludeMinPrice(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal): ActiveOrder? {
        return orders.filter {
            it.botName == botName &&
            it.direction == direction &&
            it.price != null &&
            it.price >= minPrice &&
            it.price <= maxPrice
        }.minByOrNull { it.price!! }
    }
    
    override fun deleteByDirectionAndSide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder> {
        val toDelete = orders.filter { it.botName == botName && it.direction == direction && it.orderSide == side }
        orders.removeAll(toDelete)
        return toDelete
    }
    
    override fun deleteByDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder> {
        val toDelete = orders.filter { it.botName == botName && it.direction == direction }
        orders.removeAll(toDelete)
        return toDelete
    }
    
    override fun deleteByBotName(botName: String): Iterable<ActiveOrder> {
        val toDelete = orders.filter { it.botName == botName }
        orders.removeAll(toDelete)
        return toDelete
    }
    
    override fun count(botName: String, direction: DIRECTION, side: SIDE): Long {
        return orders.count { it.botName == botName && it.direction == direction && it.orderSide == side }.toLong()
    }
    
    override fun count(botName: String): Long {
        return orders.count { it.botName == botName }.toLong()
    }
    
    override fun deleteById(id: Long) {
        orders.removeIf { it.id == id }
    }
    
    override fun deleteByOrderId(orderId: String) {
        orders.removeIf { it.orderId == orderId }
    }

    override fun getOrderByPriceAndSideBetween(botName: String, direction: DIRECTION, minPrice: BigDecimal, maxPrice: BigDecimal, side: SIDE): Iterable<ActiveOrder> {
        return orders.filter {
            it.botName == botName &&
            it.direction == direction &&
            it.price != null &&
            it.price >= minPrice &&
            it.price <= maxPrice &&
            it.orderSide == side
        }
    }

    override fun getOrderByStopPriceAndSideBetween(botName: String, direction: DIRECTION, minStopPrice: BigDecimal, maxStopPrice: BigDecimal, side: SIDE): Iterable<ActiveOrder> {
        return orders.filter {
            it.botName == botName &&
            it.direction == direction &&
            it.stopPrice != null &&
            it.stopPrice!! >= minStopPrice &&
            it.stopPrice!! <= maxStopPrice &&
            it.orderSide == side
        }
    }

    override fun saveAll(orders: Iterable<ActiveOrder>): Iterable<ActiveOrder> {
        this.orders.addAll(orders)
        return orders
    }

    override fun updateOrdersByOrderId(orders: List<ActiveOrder>): Iterable<ActiveOrder> {
        val orderIds = orders.mapNotNull { it.orderId }
        this.orders.removeIf { it.orderId in orderIds }
        this.orders.addAll(orders)
        return orders
    }

    override fun updateOrdersById(orders: List<ActiveOrder>): Iterable<ActiveOrder> {
        val ids = orders.mapNotNull { it.id }
        this.orders.removeIf { it.id in ids }
        this.orders.addAll(orders)
        return orders
    }
}