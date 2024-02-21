package bot.trade.database.service.impl

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.database.service.ActiveOrdersService
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
}