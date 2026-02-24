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
import bot.trade.analytics.GridSuitabilityService
import bot.trade.analytics.GridSuitabilityResult
import bot.trade.exchanges.clients.ExchangeEnum.Companion.newClient
import bot.trade.exchanges.clients.ClientBinance
import bot.trade.exchanges.clients.ClientByBitBase
import bot.trade.exchanges.clients.ClientByBitFutures
import bot.trade.exchanges.clients.ClientGate
import bot.trade.exchanges.clients.ClientExtended
import com.google.gson.Gson
import okhttp3.OkHttpClient as OkHttp
import okhttp3.Request as OkHttpRequest
import org.knowm.xchange.currency.CurrencyPair
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

    // ============== Grid Suitability Endpoints ==============

    data class GridSuitabilityRequest(val exchange: String, val pair: String)

    @PostMapping("/grid-suitability")
    fun gridSuitability(@RequestBody request: GridSuitabilityRequest): ResponseEntity<GridSuitabilityResult> {
        log.info("Request for /grid-suitability: exchange=${request.exchange}, pair=${request.pair}")

        return try {
            val exchangeEnum = try {
                ExchangeEnum.valueOf(request.exchange.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest()
                    .body(GridSuitabilityResult.error(request.exchange, request.pair,
                        "Unknown exchange '${request.exchange}'. Supported: BINANCE, BINANCE_FUTURES, BYBIT_SPOT, BYBIT_FUTURES, GATE, HUOBI, MEXC, BITMAX"))
            }

            val client = try {
                exchangeEnum.newClient()
            } catch (e: Exception) {
                log.warn("No config for ${request.exchange}, trying without credentials: ${e.message}")
                exchangeEnum.newClient(api = null, sec = null)
            }

            val pair = TradePair(request.pair)

            val candles = try {
                client.getCandlestickBars(pair, INTERVAL.HOURLY, 500)
            } catch (e: Exception) {
                log.error("Failed to fetch candles for ${request.pair} on ${request.exchange}: ${e.message}")
                return ResponseEntity.ok(
                    GridSuitabilityResult.error(request.exchange, request.pair,
                        "Failed to fetch data for pair '${request.pair}': ${e.message}")
                )
            } finally {
                client.close()
            }

            val result = GridSuitabilityService().analyze(request.exchange, request.pair, candles)
            ResponseEntity.ok(result)

        } catch (e: Exception) {
            log.error("Error in /grid-suitability: ${e.message}", e)
            ResponseEntity.status(500)
                .body(GridSuitabilityResult.error(request.exchange, request.pair, "Internal error: ${e.message}"))
        }
    }

    @GetMapping("/grid-suitability")
    fun gridSuitabilityPage(): ResponseEntity<String> {
        return try {
            val content = File("pages/grid-suitability.html").readText()
            ResponseEntity.ok()
                .header("Content-Type", "text/html")
                .body(content)
        } catch (e: Exception) {
            log.error("Error loading grid-suitability page: ${e.message}", e)
            ResponseEntity.status(500).body("Error loading page: ${e.message}")
        }
    }

    // ---- Scan endpoint ----

    data class GridSuitabilityScanRequest(
        val exchange: String,
        val quoteCurrency: String?,
        val limit: Int?
    )

    @PostMapping("/grid-suitability-scan")
    fun gridSuitabilityScan(@RequestBody request: GridSuitabilityScanRequest): ResponseEntity<Any> {
        log.info("Request for /grid-suitability-scan: exchange=${request.exchange}, quote=${request.quoteCurrency}, limit=${request.limit}")

        val exchangeEnum = try {
            ExchangeEnum.valueOf(request.exchange.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Response("error", "Unknown exchange: ${request.exchange}"))
        }

        val quoteCurrency = (request.quoteCurrency ?: "USDT").uppercase()
        val limit = (request.limit ?: 30).coerceIn(1, 100)

        val client = try {
            exchangeEnum.newClient()
        } catch (e: Exception) {
            log.error("Failed to create client for ${request.exchange}: ${e.message}")
            return ResponseEntity.status(500).body(Response("error", "Failed to connect to exchange: ${e.message}"))
        }

        try {
            val pairs = getExchangePairsForScan(client, request.exchange, quoteCurrency, limit)

            if (pairs.isEmpty()) {
                return ResponseEntity.ok(
                    Response("error", "Pair scanning not supported for ${request.exchange}. " +
                        "Supported exchanges for scan: BINANCE, BINANCE_FUTURES, BYBIT_SPOT, BYBIT_FUTURES, GATE, EXTENDED. " +
                        "Use the single-pair analyzer for other exchanges.")
                )
            }

            log.info("Scanning ${pairs.size} ${quoteCurrency} pairs on ${request.exchange}...")

            val suitabilityService = GridSuitabilityService()
            val results = pairs.mapNotNull { pair ->
                    try {
                        val candles = if (client is ClientGate)
                            fetchGateCandlesDirect(pair, 168)
                        else
                            client.getCandlestickBars(pair, INTERVAL.HOURLY, 168)
                        if (candles.size < 12) return@mapNotNull null
                        val r = suitabilityService.analyze(request.exchange, "${pair.first}/${pair.second}", candles)
                        if (r.error != null) null else r
                    } catch (e: Exception) {
                        log.debug("Skipping ${pair.first}/${pair.second}: ${e.message}")
                        null
                    }
                }
                .sortedByDescending { it.suitabilityScore }

            log.info("Scan complete: ${results.size} pairs analyzed on ${request.exchange}")
            return ResponseEntity.ok(results)

        } catch (e: Exception) {
            log.error("Error in scan for ${request.exchange}: ${e.message}", e)
            return ResponseEntity.status(500).body(Response("error", "Scan failed: ${e.message}"))
        } finally {
            client.close()
        }
    }

    private fun getExchangePairsForScan(client: Client, exchangeName: String, quoteCurrency: String, limit: Int): List<TradePair> {
        return try {
            when (client) {
                is ClientBinance -> {
                    client.marketDataService.getTickers(null)
                        .filter { ticker -> ticker.instrument is CurrencyPair &&
                            (ticker.instrument as CurrencyPair).counter.currencyCode.equals(quoteCurrency, ignoreCase = true) }
                        .sortedByDescending { ticker ->
                            (ticker.instrument as CurrencyPair).let { _ ->
                                try { ticker.quoteVolume } catch (e: Exception) { ticker.volume }
                            } ?: BigDecimal.ZERO
                        }
                        .take(limit)
                        .map { ticker ->
                            val cp = ticker.instrument as CurrencyPair
                            TradePair(cp.base.currencyCode, cp.counter.currencyCode)
                        }
                }
                is ClientByBitBase -> getByBitPairsForScan(client, quoteCurrency, limit)
                is ClientGate -> getGatePairsForScan(quoteCurrency, limit)
                is ClientExtended -> client.getAllPairs()
                    .filter { it.second.equals(quoteCurrency, ignoreCase = true) }
                    .take(limit)
                else -> {
                    log.warn("Pair scanning not supported for $exchangeName (${client::class.simpleName})")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to get pairs for $exchangeName: ${e.message}", e)
            emptyList()
        }
    }

    // Gate.io public API helpers (no auth required)

    private fun getGatePairsForScan(quoteCurrency: String, limit: Int): List<TradePair> {
        data class GateTicker(val currency_pair: String?, val vol_24h_quote: String?)

        return try {
            val body = OkHttp().newCall(
                OkHttpRequest.Builder().url("https://api.gateio.ws/api/v4/spot/tickers").get().build()
            ).execute().use { it.body?.string() ?: return emptyList() }

            Gson().fromJson(body, Array<GateTicker>::class.java)
                .filter { it.currency_pair?.endsWith("_$quoteCurrency", ignoreCase = true) == true }
                .sortedByDescending { it.vol_24h_quote?.toBigDecimalOrNull() ?: BigDecimal.ZERO }
                .take(limit)
                .mapNotNull { t ->
                    val pair = t.currency_pair ?: return@mapNotNull null
                    val base = pair.removeSuffix("_$quoteCurrency").removeSuffix("_${quoteCurrency.lowercase()}")
                    if (base.isBlank()) null else TradePair(base, quoteCurrency)
                }
        } catch (e: Exception) {
            log.error("Failed to fetch Gate pairs: ${e.message}", e)
            emptyList()
        }
    }

    private fun fetchGateCandlesDirect(pair: TradePair, limit: Int): List<Candlestick> {
        val symbol = "${pair.first}_${pair.second}"
        val url = "https://api.gateio.ws/api/v4/spot/candlesticks?currency_pair=$symbol&interval=1h&limit=$limit"

        return try {
            val body = OkHttp().newCall(
                OkHttpRequest.Builder().url(url).get().build()
            ).execute().use { it.body?.string() ?: return emptyList() }

            // Response format: [[timestamp, volume_base, close, high, low, open, volume_quote, is_closed], ...]
            val raw = Gson().fromJson(body, Array<Array<String>>::class.java)
            raw.mapNotNull { c ->
                if (c.size < 6) return@mapNotNull null
                val ts = c[0].toLongOrNull()?.times(1000) ?: return@mapNotNull null
                Candlestick(
                    openTime = ts,
                    closeTime = ts + 3_600_000L,
                    open = c[5].toBigDecimalOrNull() ?: return@mapNotNull null,
                    high = c[3].toBigDecimalOrNull() ?: return@mapNotNull null,
                    low = c[4].toBigDecimalOrNull() ?: return@mapNotNull null,
                    close = c[2].toBigDecimalOrNull() ?: return@mapNotNull null,
                    volume = c[1].toBigDecimalOrNull() ?: BigDecimal.ZERO
                )
            }
        } catch (e: Exception) {
            log.error("Failed to fetch Gate candles for ${pair.first}/${pair.second}: ${e.message}", e)
            emptyList()
        }
    }

    private fun getByBitPairsForScan(client: ClientByBitBase, quoteCurrency: String, limit: Int): List<TradePair> {
        val category = if (client is ClientByBitFutures) "linear" else "spot"
        val url = "https://api.bybit.com/v5/market/tickers?category=$category"

        data class ByBitSymbol(val symbol: String?, val turnover24h: String?)
        data class ByBitResult(val list: List<ByBitSymbol>?)
        data class ByBitResponse(val result: ByBitResult?)

        return try {
            val body = OkHttp().newCall(OkHttpRequest.Builder().url(url).get().build())
                .execute().use { it.body?.string() ?: return emptyList() }

            val response = Gson().fromJson(body, ByBitResponse::class.java)
            (response.result?.list ?: emptyList())
                .filter { it.symbol?.endsWith(quoteCurrency, ignoreCase = true) == true }
                .sortedByDescending { it.turnover24h?.toBigDecimalOrNull() ?: BigDecimal.ZERO }
                .take(limit)
                .mapNotNull { sym ->
                    val symbol = sym.symbol ?: return@mapNotNull null
                    val base = symbol.removeSuffix(quoteCurrency).removeSuffix(quoteCurrency.lowercase())
                    if (base.isBlank()) null else TradePair(base, quoteCurrency)
                }
        } catch (e: Exception) {
            log.error("Failed to fetch ByBit pairs: ${e.message}", e)
            emptyList()
        }
    }
}
