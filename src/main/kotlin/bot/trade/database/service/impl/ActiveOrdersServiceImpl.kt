package bot.trade.database.service.impl

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.database.service.ActiveOrdersService
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActiveOrdersServiceImpl(@Autowired open val activeOrdersRepository: ActiveOrdersRepository) :
    ActiveOrdersService {

    @Transactional
    override fun saveOrder(order: ActiveOrder): ActiveOrder = activeOrdersRepository.save(order)

    @Transactional
    override fun getOrderById(id: Long): ActiveOrder? = activeOrdersRepository.findOrderById(id)

    @Transactional
    override fun deleteById(id: Long) = activeOrdersRepository.deleteById(id)

    @Transactional
    override fun deleteByOrderId(orderId: String) = activeOrdersRepository.deleteByOrderId(orderId)

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