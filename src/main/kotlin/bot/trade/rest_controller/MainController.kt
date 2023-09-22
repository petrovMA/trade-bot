package bot.trade.rest_controller

import bot.trade.TaskExecutor
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
import bot.telegram.TelegramBot
import mu.KLogger
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

@RestController
class MainController(orderService: OrderService) {
    final val log: KLogger = KotlinLogging.logger {}
    final val bot: TelegramBot

    init {
        val exchangeFile = File("exchange")
        val exchangeBotsFiles = "exchangeBots"
        val taskExecutor = TaskExecutor(LinkedBlockingDeque())
        val propConf = readConf("common.conf") ?: throw RuntimeException("Can't read Config File!")

        taskExecutor.start()

        bot = try {
            TelegramBot(
                chatId = propConf.getString("bot_properties.bot.chat_id"),
                adminId = propConf.getString("bot_properties.bot.admin_id"),
                exchangeBotsFiles = exchangeBotsFiles,
                orderService = orderService,
                botUsername = propConf.getString("bot_properties.bot.bot_name"),
                botToken = propConf.getString("bot_properties.bot.bot_token"),
                defaultCommands = mapOf(),
                intervalCandlestick = null,
                intervalStatistic = null,
                timeDifference = null,
                candlestickDataCommandStr = null,
                candlestickDataPath = mapOf(),
                taskQueue = taskExecutor.getQueue(),
                exchangeFiles = exchangeFile
            ).also { TelegramBotsApi(DefaultBotSession::class.java).registerBot(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            log.error(e.message, e)
            throw e
        }
    }
    data class Response(val status: String, val data: Any)
    data class BotsInfoResponse(val settings: BotSettings, val position: Any)

    @PostMapping("/endpoint/trade")
    fun receivePostTrade(@RequestBody request: String): ResponseEntity<Response> {
        log.info("Request for /endpoint/trade = $request")

        // process the request here and prepare the response
        val response = Response("success", "Received $request")
        log.debug("Response for /endpoint/trade = {}", response)

        bot.communicator.sendOrder(request)

        return ResponseEntity.ok(response)
    }

    @GetMapping("/positions")
    fun positionsGet(): ResponseEntity<Any> {
        // process the request here and prepare the response
        val infoResponse = bot.communicator.getInfo().map { BotsInfoResponse(it.first, it.second) }
        log.info("Response for /positions = $infoResponse")

        return ResponseEntity.ok(infoResponse)
    }
}
