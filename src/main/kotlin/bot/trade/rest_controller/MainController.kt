package bot.trade.rest_controller

import bot.communicator.BotType
import bot.trade.TaskExecutor
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
import bot.communicator.TelegramBot
import bot.communicator.Communicator
import bot.trade.database.service.ActiveOrdersService
import bot.trade.exchanges.emulate.TestBalance
import bot.trade.exchanges.params.BotEmulateParams
import bot.trade.exchanges.params.BotSettings
import bot.trade.exchanges.params.BotSettingsGrid
import bot.trade.libs.CustomFileLoggingProcessor
import bot.trade.libs.deserialize
import bot.trade.balance.BalanceRequirement
import bot.trade.exchanges.GridOrders
import bot.trade.exchanges.clients.ExchangeEnum
import exchange_api.gate.rest.client.GateRestApiClient
import exchange_api.gate.rest.client.GateTradeResponse
import bot.trade.analytics.GridAnalyticsService
import bot.trade.analytics.GridAnalyticsResult
import java.math.BigDecimal
import mu.KLogger
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

@RestController
class MainController(orderService: OrderService, private val activeOrdersService: ActiveOrdersService) {
    final val log: KLogger = KotlinLogging.logger {}
    final val communicator: Communicator

    init {
        val exchangeFile = File("exchange")
        val exchangeBotsFiles = "exchangeBots"
        val taskExecutor = TaskExecutor(LinkedBlockingDeque())
        val propConf = readConf("common.conf") ?: throw RuntimeException("Can't read Config File!")

        val logMessageQueue = LinkedBlockingDeque<CustomFileLoggingProcessor.Message>()
        CustomFileLoggingProcessor(logMessageQueue)

        taskExecutor.start()


        communicator = Communicator(
            config = propConf,
            intervalCandlestick = null,
            exchangeBotsFiles = exchangeBotsFiles,
            orderService = orderService,
            intervalStatistic = null,
            timeDifference = null,
            activeOrdersService = activeOrdersService,
            candlestickDataCommandStr = null,
            taskQueue = taskExecutor.getQueue(),
            exchangeFiles = exchangeFile,
            logMessageQueue = logMessageQueue,
            botType = BotType.valueOf(propConf.getString("bot_properties.bot.type").uppercase())
        )
    }

    data class Response(val status: String, val data: Any)

    data class PositionResponse(
        val pair: String,
        val marketPrice: Double,
        val unrealisedPnl: Double,
        val realisedPnl: Double,
        val entryPrice: Double,
        val breakEvenPrice: Double,
        val leverage: Int,
        val liqPrice: Double,
        val size: Double,
        val side: String
    )

    data class BotInfoResponse(val settings: BotSettings, val position: PositionResponse?)


    @PostMapping("/emulate")
    fun emulate(@RequestBody request: String): ResponseEntity<TestBalance> {
        log.info("Request for /endpoint/trade = $request")

        val params = request.deserialize<BotEmulateParams>()

        // process the request here and prepare the response
        val response = Response("success", "Received $request")
        log.debug("Response for /endpoint/trade = {}", response)

        val result = communicator.emulate(params)

        return ResponseEntity.ok(result.first)
    }

    data class BalanceRequest(val price: BigDecimal, val bot_params: BotSettingsGrid)

    @PostMapping("/get_necessary_balance")
    fun getNecessaryBalance(@RequestBody request: String): ResponseEntity<BalanceRequirement> {
        log.info("Request for /get_necessary_balance = $request")

        val params = request.deserialize<BalanceRequest>()

        try {
            val result = GridOrders(params.price, botSettings = params.bot_params)
                .calculateRequiredBalance()
            
            log.info("Balance calculation result: $result")
            return ResponseEntity.ok(result)
            
        } catch (e: Exception) {
            log.error("Error calculating balance: ${e.message}", e)
            throw e
        }
    }

    @PostMapping("/endpoint/trade")
    fun receivePostTrade(@RequestBody request: String): ResponseEntity<Response> {
        log.info("Request for /endpoint/trade = $request")

        // process the request here and prepare the response
        val response = Response("success", "Received $request")
        log.debug("Response for /endpoint/trade = {}", response)

        communicator.sendOrder(request)

        return ResponseEntity.ok(response)
    }

    @GetMapping("/positions")
    fun positionsGet(): ResponseEntity<List<BotInfoResponse>> {
        val infoResponse = communicator.getInfo()
        log.info("Response for /positions = $infoResponse")
        return ResponseEntity.ok(infoResponse)
    }

    @GetMapping("/error")
    fun error(): ResponseEntity<Any> {
        val botsList = communicator
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

    @PostMapping("/create_bot")
    fun createBot(@RequestBody request: String): ResponseEntity<Response> {
        log.info("Request for /create_bot = $request")

        val message = "/create\n$request"
        communicator.onUpdate(message)

        val response = Response("success", "Bot creation command processed")
        log.info("Response for /create_bot = $response")

        return ResponseEntity.ok(response)
    }

    @PostMapping("/load_bot")
    fun loadBot(@RequestBody botName: String): ResponseEntity<Response> {
        log.info("Request for /load_bot with botName = $botName")

        return try {
            val message = "/load $botName"
            val result = communicator.onUpdateWithResult(message)

            log.info("Bot load result: $result")
            ResponseEntity.ok(Response("success", result))
        } catch (e: Exception) {
            log.error("Error loading bot $botName: ${e.message}", e)
            ResponseEntity.status(500).body(Response("error", "Error loading bot: ${e.message}"))
        }
    }

    @PostMapping("/start_bot")
    fun startBot(@RequestBody botName: String): ResponseEntity<Response> {
        log.info("Request for /start_bot with botName = $botName")

        return try {
            val message = "/start $botName"
            val result = communicator.onUpdateWithResult(message)

            // Check if result contains error messages
            val isError = result.contains("Insufficient", ignoreCase = true) ||
                          result.contains("Can't receive", ignoreCase = true) ||
                          result.contains("not exist", ignoreCase = true) ||
                          result.contains("error", ignoreCase = true) ||
                          result.contains("failed", ignoreCase = true) ||
                          result.contains("must have", ignoreCase = true)

            if (isError) {
                log.warn("Bot start failed: $result")
                ResponseEntity.status(400).body(Response("error", result))
            } else {
                log.info("Bot $botName started successfully: $result")
                ResponseEntity.ok(Response("success", result))
            }
        } catch (e: Exception) {
            log.error("Error starting bot $botName: ${e.message}", e)
            ResponseEntity.status(500).body(Response("error", "Error starting bot: ${e.message}"))
        }
    }

    @PostMapping("/resume_bot")
    fun resumeBot(@RequestBody botName: String): ResponseEntity<Response> {
        log.info("Request for /resume_bot with botName = $botName")

        return try {
            val message = "/resume $botName"
            val result = communicator.onUpdateWithResult(message)

            // Check if result contains error messages
            val isError = result.contains("Insufficient", ignoreCase = true) ||
                          result.contains("Can't receive", ignoreCase = true) ||
                          result.contains("not exist", ignoreCase = true) ||
                          result.contains("error", ignoreCase = true) ||
                          result.contains("failed", ignoreCase = true) ||
                          result.contains("must have", ignoreCase = true)

            if (isError) {
                log.warn("Bot resume failed: $result")
                ResponseEntity.status(400).body(Response("error", result))
            } else {
                log.info("Bot $botName resumed successfully: $result")
                ResponseEntity.ok(Response("success", result))
            }
        } catch (e: Exception) {
            log.error("Error resuming bot $botName: ${e.message}", e)
            ResponseEntity.status(500).body(Response("error", "Error resuming bot: ${e.message}"))
        }
    }

    @PostMapping("/stop_bot")
    fun stopBot(@RequestBody botName: String): ResponseEntity<Response> {
        log.info("Request for /stop_bot with botName = $botName")

        return try {
            val message = "/stop $botName"
            val result = communicator.onUpdateWithResult(message)

            val isError = result.contains("not exist", ignoreCase = true) ||
                          result.contains("error", ignoreCase = true) ||
                          result.contains("failed", ignoreCase = true)

            if (isError) {
                log.warn("Bot stop failed: $result")
                ResponseEntity.status(400).body(Response("error", result))
            } else {
                log.info("Bot $botName stopped successfully: $result")
                ResponseEntity.ok(Response("success", result))
            }
        } catch (e: Exception) {
            log.error("Error stopping bot $botName: ${e.message}", e)
            ResponseEntity.status(500).body(Response("error", "Error stopping bot: ${e.message}"))
        }
    }

    @PostMapping("/delete_bot")
    fun deleteBot(@RequestBody botName: String): ResponseEntity<Response> {
        log.info("Request for /delete_bot with botName = $botName")

        return try {
            val message = "/delete $botName"
            val result = communicator.onUpdateWithResult(message)

            val isError = result.contains("not exist", ignoreCase = true) ||
                          result.contains("error", ignoreCase = true) ||
                          result.contains("failed", ignoreCase = true)

            if (isError) {
                log.warn("Bot delete failed: $result")
                ResponseEntity.status(400).body(Response("error", result))
            } else {
                log.info("Bot $botName deleted successfully: $result")
                ResponseEntity.ok(Response("success", result))
            }
        } catch (e: Exception) {
            log.error("Error deleting bot $botName: ${e.message}", e)
            ResponseEntity.status(500).body(Response("error", "Error deleting bot: ${e.message}"))
        }
    }

    @PostMapping("/force_sync_bot")
    fun forceSyncBot(@RequestBody botName: String): ResponseEntity<Response> {
        log.info("Request for /force_sync_bot with botName = $botName")

        return try {
            val message = "/forcesync $botName"
            val result = communicator.onUpdateWithResult(message)

            val isError = result.contains("not found", ignoreCase = true) ||
                          result.contains("not running", ignoreCase = true) ||
                          result.contains("error", ignoreCase = true)

            if (isError) {
                log.warn("Force sync failed: $result")
                ResponseEntity.status(400).body(Response("error", result))
            } else {
                log.info("Force sync queued for $botName: $result")
                ResponseEntity.ok(Response("success", result))
            }
        } catch (e: Exception) {
            log.error("Error force syncing bot $botName: ${e.message}", e)
            ResponseEntity.status(500).body(Response("error", "Error: ${e.message}"))
        }
    }

    data class BotConfigInfo(
        val name: String,
        val settings: BotSettings
    )

    @GetMapping("/bot_configs")
    fun getBotConfigs(): ResponseEntity<List<BotConfigInfo>> {
        log.info("Request for /bot_configs")

        val exchangeBotsPath = File("exchangeBots")

        if (!exchangeBotsPath.exists() || !exchangeBotsPath.isDirectory) {
            log.warn("exchangeBots directory not found")
            return ResponseEntity.ok(emptyList())
        }

        val botConfigs = exchangeBotsPath.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { botDir ->
                val settingsFile = File(botDir, "settings.json")
                if (settingsFile.exists()) {
                    try {
                        val settingsJson = settingsFile.readText()
                        val settings = settingsJson.deserialize<BotSettings>()
                        BotConfigInfo(botDir.name, settings)
                    } catch (e: Exception) {
                        log.error("Error reading settings for ${botDir.name}: ${e.message}")
                        null
                    }
                } else {
                    null
                }
            }
            ?.sortedBy { it.name }
            ?: emptyList()

        log.info("Found ${botConfigs.size} bot configurations")
        return ResponseEntity.ok(botConfigs)
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

        val hedge = communicator.getHedgeModule(botName)
        val trend = communicator.getTrend(botName)
        val (positionLong, positionShort) = communicator.positions(botName) ?: (null to null)
        val (maxPriceInOrderLong, minPriceInOrderLong, maxPriceInOrderShort, minPriceInOrderShort, currentPrice)
                = communicator.orderBorders(botName) ?: listOf(null, null, null, null, null)

        val strPrices = "maxPriceInOrderLong = $maxPriceInOrderLong, minPriceInOrderLong = $minPriceInOrderLong, " +
                "maxPriceInOrderShort = $maxPriceInOrderShort, minPriceInOrderShort = $minPriceInOrderShort, " +
                "currentPrice = $currentPrice"

        val botsList = communicator
            .getBotsList()
            .joinToString(separator = "") {
                """<a href="/orders?botName=$it" class="btn btn-primary">$it</a>""".trimIndent()
            }

        var rowNum = 1

        val longTableContent = activeOrdersService.getOrders(botName, DIRECTION.LONG)
            .toList()
            .sortedBy { it.price }
            .joinToString(prefix = "<tbody>", postfix = "</tbody>", separator = "") {
                """
                    <tr class="${if (it.orderSide == SIDE.BUY) "buy" else "sell"}">
            <th scope="row">${rowNum++}</th>
            <td>${it.price}</td>
            <td>${it.stopPrice}</td>
            <td>${it.lastBorderPrice}</td>
            <td>${it.amount}</td>
        </tr>
                """.trimIndent()
            }

        rowNum = 1

        val shortTableContent = activeOrdersService.getOrders(botName, DIRECTION.SHORT)
            .toList()
            .sortedBy { it.price }
            .run { reversed() }
            .joinToString(prefix = "<tbody>", postfix = "</tbody>", separator = "") {
                """
                    <tr class="${if (it.orderSide == SIDE.BUY) "buy" else "sell"}">
            <th scope="row">${rowNum++}</th>
            <td>${it.price}</td>
            <td>${it.stopPrice}</td>
            <td>${it.lastBorderPrice}</td>
            <td>${it.amount}</td>
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
                    .replace("${'$'}positions", "$positionLong<br>$positionShort")
            )
    }

    // ============== Grid Analysis Endpoints ==============

    data class TradeAnalysisRequest(
        val exchange: String,
        val symbol: String,
        val from: Long? = null,
        val to: Long? = null,
        val minAmount: BigDecimal? = null,
        val maxAmount: BigDecimal? = null
    )

    data class TradeAnalysisResponse(
        val trades: List<GateTradeResponse>,
        val summary: TradeSummary
    )

    data class TradeSummary(
        val totalTrades: Int,
        val buyTrades: Int,
        val sellTrades: Int,
        val totalBuyAmount: BigDecimal,
        val totalSellAmount: BigDecimal,
        val totalBuyValue: BigDecimal,
        val totalSellValue: BigDecimal,
        val avgBuyPrice: BigDecimal,
        val avgSellPrice: BigDecimal,
        val totalFees: BigDecimal,
        val grossProfit: BigDecimal,
        val netProfit: BigDecimal,
        val profitPercentage: BigDecimal
    )

    @PostMapping("/trades")
    fun getTrades(@RequestBody request: TradeAnalysisRequest): ResponseEntity<TradeAnalysisResponse> {
        log.info("Request for /trades: $request")

        return try {
            val apiKey: String
            val apiSecret: String

            when (request.exchange.lowercase()) {
                "gate", "gateio", "gate.io" -> {
                    val gateConf = readConf("exchangeConfigs/GATE.conf")
                        ?: throw RuntimeException("Can't read Gate.io config file!")
                    apiKey = gateConf.getString("api")
                    apiSecret = gateConf.getString("sec")
                }
                else -> {
                    return ResponseEntity.badRequest()
                        .body(TradeAnalysisResponse(
                            emptyList(),
                            createEmptySummary()
                        ))
                }
            }

            val gateClient = GateRestApiClient(apiKey, apiSecret)

            var trades = gateClient.getMyTrades(
                symbol = request.symbol,
                from = request.from,
                to = request.to,
                limit = 1000
            )

            // Filter by order VALUE in USDT (amount × price) if specified
            if (request.minAmount != null || request.maxAmount != null) {
                trades = trades.filter { trade ->
                    val orderValue = trade.amount * trade.price  // Total value in USDT
                    val minOk = request.minAmount == null || orderValue >= request.minAmount
                    val maxOk = request.maxAmount == null || orderValue <= request.maxAmount
                    minOk && maxOk
                }
            }

            val summary = calculateTradeSummary(trades)

            log.info("Returning ${trades.size} trades with summary: $summary")
            ResponseEntity.ok(TradeAnalysisResponse(trades, summary))

        } catch (e: Exception) {
            log.error("Error getting trades: ${e.message}", e)
            ResponseEntity.status(500)
                .body(TradeAnalysisResponse(emptyList(), createEmptySummary()))
        }
    }

    @PostMapping("/grid-analytics")
    fun getGridAnalytics(@RequestBody request: TradeAnalysisRequest): ResponseEntity<GridAnalyticsResult> {
        log.info("Request for /grid-analytics: $request")

        return try {
            val apiKey: String
            val apiSecret: String

            when (request.exchange.lowercase()) {
                "gate", "gateio", "gate.io" -> {
                    val gateConf = readConf("exchangeConfigs/GATE.conf")
                        ?: throw RuntimeException("Can't read Gate.io config file!")
                    apiKey = gateConf.getString("api")
                    apiSecret = gateConf.getString("sec")
                }
                else -> {
                    log.warn("Unsupported exchange: ${request.exchange}")
                    return ResponseEntity.badRequest().body(GridAnalyticsResult.empty())
                }
            }

            val gateClient = GateRestApiClient(apiKey, apiSecret)

            val trades = gateClient.getMyTrades(
                symbol = request.symbol,
                from = request.from,
                to = request.to,
                limit = 1000
            )

            val gridAnalyticsService = GridAnalyticsService()
            val result = gridAnalyticsService.analyzeTrades(
                trades = trades,
                orderValueMin = request.minAmount,
                orderValueMax = request.maxAmount
            )

            log.info("Grid analytics result: matchedPairs=${result.matchedPairs}, gridStep=${result.gridStep}, netProfit=${result.netProfit}")
            ResponseEntity.ok(result)

        } catch (e: Exception) {
            log.error("Error getting grid analytics: ${e.message}", e)
            ResponseEntity.status(500).body(GridAnalyticsResult.empty())
        }
    }

    @GetMapping("/grid-analysis")
    fun gridAnalysisPage(): ResponseEntity<String> {
        return try {
            val content = File("pages/grid-analysis.html").readText()
            ResponseEntity.ok()
                .header("Content-Type", "text/html")
                .body(content)
        } catch (e: Exception) {
            log.error("Error loading grid-analysis page: ${e.message}", e)
            ResponseEntity.status(500).body("Error loading page: ${e.message}")
        }
    }

    private fun calculateTradeSummary(trades: List<GateTradeResponse>): TradeSummary {
        val buyTrades = trades.filter { it.side == "Buy" }
        val sellTrades = trades.filter { it.side == "Sell" }

        val totalBuyAmount = buyTrades.sumOf { it.amount }
        val totalSellAmount = sellTrades.sumOf { it.amount }
        val totalBuyValue = buyTrades.sumOf { it.total }
        val totalSellValue = sellTrades.sumOf { it.total }
        val totalFees = trades.sumOf { it.fee }

        val avgBuyPrice = if (totalBuyAmount > BigDecimal.ZERO)
            totalBuyValue.divide(totalBuyAmount, 8, java.math.RoundingMode.HALF_UP)
            else BigDecimal.ZERO
        val avgSellPrice = if (totalSellAmount > BigDecimal.ZERO)
            totalSellValue.divide(totalSellAmount, 8, java.math.RoundingMode.HALF_UP)
            else BigDecimal.ZERO

        // Gross profit = sell value - buy value (for matched amounts)
        val matchedAmount = minOf(totalBuyAmount, totalSellAmount)
        val grossProfit = if (matchedAmount > BigDecimal.ZERO) {
            (avgSellPrice - avgBuyPrice) * matchedAmount
        } else {
            BigDecimal.ZERO
        }

        val netProfit = grossProfit - totalFees

        val profitPercentage = if (totalBuyValue > BigDecimal.ZERO) {
            netProfit.divide(totalBuyValue, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)
        } else {
            BigDecimal.ZERO
        }

        return TradeSummary(
            totalTrades = trades.size,
            buyTrades = buyTrades.size,
            sellTrades = sellTrades.size,
            totalBuyAmount = totalBuyAmount,
            totalSellAmount = totalSellAmount,
            totalBuyValue = totalBuyValue,
            totalSellValue = totalSellValue,
            avgBuyPrice = avgBuyPrice,
            avgSellPrice = avgSellPrice,
            totalFees = totalFees,
            grossProfit = grossProfit,
            netProfit = netProfit,
            profitPercentage = profitPercentage
        )
    }

    private fun createEmptySummary() = TradeSummary(
        totalTrades = 0,
        buyTrades = 0,
        sellTrades = 0,
        totalBuyAmount = BigDecimal.ZERO,
        totalSellAmount = BigDecimal.ZERO,
        totalBuyValue = BigDecimal.ZERO,
        totalSellValue = BigDecimal.ZERO,
        avgBuyPrice = BigDecimal.ZERO,
        avgSellPrice = BigDecimal.ZERO,
        totalFees = BigDecimal.ZERO,
        grossProfit = BigDecimal.ZERO,
        netProfit = BigDecimal.ZERO,
        profitPercentage = BigDecimal.ZERO
    )
}
