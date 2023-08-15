package bot.telegram.notificator.database.service.impl

import bot.telegram.notificator.database.data.entities.Order
import bot.telegram.notificator.database.data.entities.NotificationType
import bot.telegram.notificator.database.repositories.OrdersRepository
import bot.telegram.notificator.database.service.OrderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderServiceImpl(@Autowired open val ordersRepository: OrdersRepository) : OrderService {

    @Transactional
    override fun saveOrder(order: Order): Order = ordersRepository.save(order)

    @Transactional
    override fun getOrderById(id: Int): Order? = ordersRepository.findOrderById(id)

    @Transactional
    override fun deleteById(id: Long): Order? = ordersRepository.deleteById(Order(id))

    @Transactional
    override fun getAllOrdersByBotName(botName: String): Iterable<Order>? = ordersRepository.findAllByBotName(botName)

    @Transactional
    override fun getAllOrdersByBotNameAndNotificationType(botName: String, notificationType: NotificationType): Iterable<Order>? =
        ordersRepository.findAllByBotNameAndNotificationType(botName, notificationType)
}