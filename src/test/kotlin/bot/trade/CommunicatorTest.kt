package bot.trade

import bot.trade.database.service.impl.ActiveOrdersServiceImpl
import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.exchanges.clients.BotEmulateParams
import bot.trade.libs.CustomFileLoggingProcessor
import bot.trade.libs.deserialize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import utils.resourceFile
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

@DataJpaTest
@ActiveProfiles("test")
class CommunicatorTest {

    @Autowired
    private lateinit var repository: ActiveOrdersRepository

//    @Test todo:: too long test
    fun testCommunicator() {

        val taskQueue = LinkedBlockingDeque<Thread>()
        val logMessageQueue = LinkedBlockingDeque<CustomFileLoggingProcessor.Message>()

        val botParams = resourceFile<CommunicatorTest>("communicatorEmulateSettings.json")
            .readText()
            .deserialize<BotEmulateParams>()

        val bot = Communicator(
            intervalCandlestick = null,
            intervalStatistic = null,
            timeDifference = null,
            activeOrdersService = ActiveOrdersServiceImpl(repository),
            candlestickDataCommandStr = null,
            candlestickDataPath = mapOf(),
            taskQueue = taskQueue,
            exchangeFiles = File(""),
            logMessageQueue = logMessageQueue,
            exchangeBotsFiles = "",
            sendFile = {}
        ) { message, _ -> println(message) }

        val result = bot.emulate(botParams)

        println(result.first)
    }
}