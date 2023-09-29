package bot.trade.rest_controller

import bot.trade.TaskExecutor
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
import bot.telegram.TelegramBot
import mu.KLogger
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import utils.resourceFile
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

    @GetMapping("/orders")
    fun ordersGet(@RequestParam botName: String): ResponseEntity<String> {

        val tableHeader = """
        <thead class="thead-dark">
        <tr>
            <th scope="col">#</th>
            <th scope="col">In Price</th>
            <th scope="col">Stop Price</th>
            <th scope="col">Border Price</th>
            <th scope="col">Size</th>
        </tr>
        </thead>
        """.trimIndent()

        val infoResponse = bot.communicator.getOrders(botName)
        log.info("Response for /orders = $infoResponse")

        if (infoResponse == null) {

            val botsList = bot.communicator
                .getBotsList()
                .joinToString {
                    """
                        <div class="text-center mt-4">
                            <a href="/orders?botName=$it" class="btn btn-primary">$it</a>
                        </div>
                    """.trimIndent()
                }

            return ResponseEntity.ok()
                .header("Content-Type", "text/html")
                .body(
                    resourceFile<MainController>("404.html")
                        .readText()
                        .replace("${'$'}content", botsList)
                )
        }

        var rowNum = 1

        val tableContent = infoResponse
            .map { it.value }
            .sortedBy { it.price }
            .joinToString(prefix = "<tbody>", postfix = "</tbody>") {
                """
                    <tr>
            <th scope="row">${rowNum++}</th>
            <td>${it.price}</td>
            <td>${it.stopPrice}</td>
            <td>${it.lastBorderPrice}</td>
            <td>${it.origQty}</td>
        </tr>
                """.trimIndent()
            }

        return ResponseEntity.ok()
            .header("Content-Type", "text/html")
            .body(
                resourceFile<MainController>("orders.html")
                    .readText()
                    .replace("${'$'}content", tableHeader + tableContent)
            )
    }
}
