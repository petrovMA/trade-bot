package bot.trade.database.service.impl

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.database.service.ActiveOrdersService
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ActiveOrdersServiceImpl(@Autowired open val activeOrdersRepository: ActiveOrdersRepository) :
    ActiveOrdersService {

    @Transactional
    override fun saveOrder(order: ActiveOrder): ActiveOrder = activeOrdersRepository.save(order)

    @Transactional
    override fun updateOrder(order: ActiveOrder): ActiveOrder {
        if (order.id == null)
            throw RuntimeException("Can't update order without ID, order = $order")

        order.orderId?.let { deleteByOrderId(it) }

        return activeOrdersRepository.save(order)
    }

    @Transactional
    override fun getOrderById(id: Long): ActiveOrder? = activeOrdersRepository.findOrderById(id)

    @Transactional
    override fun getOrderByOrderId(botName: String, orderId: String): ActiveOrder? =
        activeOrdersRepository.findByBotNameAndOrderId(botName, orderId)

    @Transactional
    override fun getOrderByOrderId(orderId: String): ActiveOrder? =
        activeOrdersRepository.findByOrderId(orderId)

    @Transactional
    override fun deleteById(id: Long) = activeOrdersRepository.deleteById(id)

    @Transactional
    override fun deleteByOrderId(orderId: String) = activeOrdersRepository.deleteByOrderId(orderId)

    @Transactional
    override fun getOrders(botName: String, direction: DIRECTION): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndDirection(botName, direction)

    @Transactional
    override fun getOrdersByPair(botName: String, tradePair: String): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndTradePair(botName, tradePair)

    @Transactional
    override fun getOrdersBySide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndDirectionAndOrderSide(botName, direction, side)

    @Transactional
    override fun getOrderWithMaxPrice(botName: String, direction: DIRECTION, maxPrice: BigDecimal): ActiveOrder? =
        activeOrdersRepository.findTopByBotNameAndDirectionAndPriceLessThanEqualOrderByPriceDesc(
            botName,
            direction,
            maxPrice
        )

    @Transactional
    override fun getOrderWithMinPrice(botName: String, direction: DIRECTION, minPrice: BigDecimal): ActiveOrder? =
        activeOrdersRepository.findTopByBotNameAndDirectionAndPriceGreaterThanEqualOrderByPriceAsc(
            botName,
            direction,
            minPrice
        )

    @Transactional
    override fun getOrdersWithMaxPriceBySide(botName: String, side: SIDE, maxPrice: BigDecimal): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndOrderSideAndPriceLessThanEqualOrderByPriceDesc(
            botName,
            side,
            maxPrice
        )

    @Transactional
    override fun getOrdersWithMinPriceBySide(botName: String, side: SIDE, minPrice: BigDecimal): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndOrderSideAndPriceGreaterThanEqualOrderByPriceAsc(
            botName,
            side,
            minPrice
        )

    @Transactional
    override fun count(botName: String, direction: DIRECTION, side: SIDE): Long =
        activeOrdersRepository.countByBotNameAndDirectionAndOrderSide(botName, direction, side)

    @Transactional
    override fun count(botName: String): Long = activeOrdersRepository.countByBotName(botName)

    @Transactional
    override fun deleteByDirectionAndSide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder> =
        activeOrdersRepository.deleteByBotNameAndDirectionAndOrderSide(botName, direction, side)

    @Transactional
    override fun deleteByDirection(botName: String, direction: DIRECTION): Iterable<ActiveOrder> =
        activeOrdersRepository.deleteByBotNameAndDirection(botName, direction)

    @Transactional
    override fun deleteByBotName(botName: String): Iterable<ActiveOrder> =
        activeOrdersRepository.deleteByBotName(botName)

    @Transactional
    override fun getOrderByPriceBetween(
        botName: String,
        direction: DIRECTION,
        minPrice: BigDecimal,
        maxPrice: BigDecimal
    ): Iterable<ActiveOrder> = activeOrdersRepository.findByBotNameAndDirectionAndPriceGreaterThanAndPriceLessThan(
        botName,
        direction,
        minPrice,
        maxPrice
    )

    @Transactional
    override fun getTopOrderByPriceBetweenIncludeMaxPrice(
        botName: String,
        direction: DIRECTION,
        minPrice: BigDecimal,
        maxPrice: BigDecimal
    ): ActiveOrder? = activeOrdersRepository.findTopByBotNameAndDirectionAndPriceGreaterThanAndPriceLessThanEqualOrderByPriceAsc(
        botName,
        direction,
        minPrice,
        maxPrice
    )

    @Transactional
    override fun getTopOrderByPriceBetweenIncludeMinPrice(
        botName: String,
        direction: DIRECTION,
        minPrice: BigDecimal,
        maxPrice: BigDecimal
    ): ActiveOrder? = activeOrdersRepository.findTopByBotNameAndDirectionAndPriceGreaterThanEqualAndPriceLessThanOrderByPriceDesc(
        botName,
        direction,
        minPrice,
        maxPrice
    )

    @Transactional
    override fun getOrderByPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder? =
        activeOrdersRepository.findByBotNameAndDirectionAndPrice(botName, direction, price)

}