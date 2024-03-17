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
import java.util.*

@Service
class ActiveOrdersServiceImpl(@Autowired open val activeOrdersRepository: ActiveOrdersRepository) :
    ActiveOrdersService {

    @Transactional
    override fun saveOrder(order: ActiveOrder): ActiveOrder = activeOrdersRepository.save(order)

    @Transactional
    override fun getOrderById(id: Long): ActiveOrder? = activeOrdersRepository.findOrderById(id)

    @Transactional
    override fun getOrderByOrderId(botName: String, orderId: UUID): ActiveOrder? =
        activeOrdersRepository.findByBotNameAndOrderId(botName, orderId)

    @Transactional
    override fun deleteById(id: Long) = activeOrdersRepository.deleteById(id)

    @Transactional
    override fun deleteByOrderId(orderId: UUID) = activeOrdersRepository.deleteByOrderId(orderId)

    @Transactional
    override fun getOrders(botName: String, direction: DIRECTION): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndDirection(botName, direction)

    @Transactional
    override fun getOrdersBySide(botName: String, direction: DIRECTION, side: SIDE): Iterable<ActiveOrder> =
        activeOrdersRepository.findAllByBotNameAndDirectionAndOrderSide(botName, direction, side)

    @Transactional
    override fun getOrderWithMaxPrice(botName: String, direction: DIRECTION, side: SIDE): ActiveOrder? =
        activeOrdersRepository.findTopByBotNameAndDirectionAndOrderSideOrderByPriceDesc(botName, direction, side)

    @Transactional
    override fun getOrderWithMinPrice(botName: String, direction: DIRECTION, side: SIDE): ActiveOrder? =
        activeOrdersRepository.findTopByBotNameAndDirectionAndOrderSideOrderByPriceAsc(botName, direction, side)

    @Transactional
    override fun count(botName: String, direction: DIRECTION, side: SIDE): Long =
        activeOrdersRepository.countByBotNameAndDirectionAndOrderSide(botName, direction, side)

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
    override fun getOrderByPrice(botName: String, direction: DIRECTION, price: BigDecimal): ActiveOrder? =
        activeOrdersRepository.findByBotNameAndDirectionAndPrice(botName, direction, price)

    @Transactional
    override fun getOrderWithMaxPrice(botName: String, direction: DIRECTION): ActiveOrder? =
        activeOrdersRepository.findTopByBotNameAndDirectionOrderByPriceDesc(botName, direction)

    @Transactional
    override fun getOrderWithMinPrice(botName: String, direction: DIRECTION): ActiveOrder? =
        activeOrdersRepository.findTopByBotNameAndDirectionOrderByPriceAsc(botName, direction)

    @Transactional
    override fun count(botName: String, direction: DIRECTION, side: SIDE): Long =
        activeOrdersRepository.countByBotNameAndDirectionAndOrderSide(botName, direction, side)

    @Transactional
    override fun deleteByOrderIds(vararg orderIds: String) =
        orderIds.forEach { activeOrdersRepository.deleteByOrderId(it) }

}