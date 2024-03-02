package bot.trade.rest_controller

import bot.trade.TaskExecutor
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
import bot.telegram.TelegramBot
import bot.trade.database.service.ActiveOrdersService
import bot.trade.database.service.ActiveOrdersServiceTest
import bot.trade.libs.CustomFileLoggingProcessor
import mu.KLogger
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

@RestController
class MainController(orderService: OrderService, activeOrdersService: ActiveOrdersService) {
    final val log: KLogger = KotlinLogging.logger {}
    final val bot: TelegramBot

    init {
//        ActiveOrdersServiceTest().test(activeOrdersService) // TODO :: tests before run
        val exchangeFile = File("exchange")
        val exchangeBotsFiles = "exchangeBots"
        val taskExecutor = TaskExecutor(LinkedBlockingDeque())
        val propConf = readConf("common.conf") ?: throw RuntimeException("Can't read Config File!")

        val logMessageQueue = LinkedBlockingDeque<CustomFileLoggingProcessor.Message>()
        CustomFileLoggingProcessor(logMessageQueue)

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
                logMessageQueue = logMessageQueue,
                taskQueue = taskExecutor.getQueue(),
                exchangeFiles = exchangeFile,
                tempUrlCalcHma = propConf.getString("bot_properties.hma_address_calc")
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

    @GetMapping("/error")
    fun error(): ResponseEntity<Any> {
        val botsList = bot.communicator
            .getBotsList()
            .joinToString(separator = "") {
                """<a href="/orders?botName=$it" class="btn btn-primary">$it</a>""".trimIndent()
            }

        return ResponseEntity.ok()
            .header("Content-Type", "text/html")
            .body(
                File("pages/main.html")
                    .readText()
                    .replace("${'$'}buttons", botsList)
            )
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
        val hedge = bot.communicator.getHedgeModule(botName)
        val trend = bot.communicator.getTrend(botName)
        val (maxPriceInOrderLong, minPriceInOrderLong, maxPriceInOrderShort, minPriceInOrderShort, currentPrice)
                = bot.communicator.orderBorders(botName) ?: listOf(null, null, null, null, null)

        val strPrices = "maxPriceInOrderLong = $maxPriceInOrderLong, minPriceInOrderLong = $minPriceInOrderLong, " +
                "maxPriceInOrderShort = $maxPriceInOrderShort, minPriceInOrderShort = $minPriceInOrderShort, " +
                "currentPrice = $currentPrice"

        log.info("Response for /orders = $infoResponse")

        val botsList = bot.communicator
            .getBotsList()
            .joinToString(separator = "") {
                """<a href="/orders?botName=$it" class="btn btn-primary">$it</a>""".trimIndent()
            }

        if (infoResponse == null)
            return ResponseEntity.ok()
                .header("Content-Type", "text/html")
                .body(
                    File("pages/main.html")
                        .readText()
                        .replace("${'$'}buttons", botsList)
                )


        var rowNum = 1

        val longTableContent = infoResponse
            .second
            .map { it.value }
            .sortedBy { it.price }
            .joinToString(prefix = "<tbody>", postfix = "</tbody>", separator = "") {
                """
                    <tr class="${if (it.side == SIDE.BUY) "buy" else "sell"}">
            <th scope="row">${rowNum++}</th>
            <td>${it.price}</td>
            <td>${it.stopPrice}</td>
            <td>${it.lastBorderPrice}</td>
            <td>${it.origQty}</td>
        </tr>
                """.trimIndent()
            }

        rowNum = 1

        val shortTableContent = infoResponse
            .third
            .map { it.value }
            .sortedBy { it.price }
            .run { reversed() }
            .joinToString(prefix = "<tbody>", postfix = "</tbody>", separator = "") {
                """
                    <tr class="${if (it.side == SIDE.BUY) "buy" else "sell"}">
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
                File("pages/orders.html")
                    .readText()
                    .replace("${'$'}longTable", tableHeader + longTableContent)
                    .replace("${'$'}shortTable", tableHeader + shortTableContent)
                    .replace("${'$'}trend", trend.toString() + (hedge ?: ""))
                    .replace("${'$'}prices", strPrices)
                    .replace("${'$'}buttons", botsList)
            )
    }
}
