package bot.telegram.notificator.rest_controller

import bot.telegram.notificator.TaskExecutor
import bot.telegram.notificator.database.service.OrderService
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.libs.readConf
import bot.telegram.notificator.telegram.TelegramBot
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
                exchangeBotsFiles = exchangeBotsFiles,
                orderService = orderService,
                botUsername = propConf.getString("bot_properties.bot.bot_name"),
                botToken = propConf.getString("bot_properties.bot.bot_token"),
                defaultCommands = mapOf(),
                intervalCandlestick = propConf.getDuration("bot_properties.exchange.interval_candlestick_update"),
                intervalStatistic = propConf.getDuration("bot_properties.exchange.interval_statistic"),
                timeDifference = propConf.getDuration("bot_properties.exchange.time_difference"),
                candlestickDataCommandStr = propConf.getString("bot_properties.exchange.candlestick_data_command"),
                candlestickDataPath = mapOf(
                    ExchangeEnum.BINANCE to propConf.getString("bot_properties.exchange.binance_emulate_data_path")!!,
                    ExchangeEnum.GATE to propConf.getString("bot_properties.exchange.gate_emulate_data_path")!!
                ),
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
        log.debug("Response for /endpoint/trade = $response")

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
