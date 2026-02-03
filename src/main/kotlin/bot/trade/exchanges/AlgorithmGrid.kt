package bot.trade.exchanges

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.service.ActiveOrdersService
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.clients.CommonExchangeData
import bot.trade.exchanges.clients.ExchangeEnum.Companion.newClient
import bot.trade.exchanges.params.BotSettings
import bot.trade.exchanges.params.BotSettingsGrid
import bot.trade.exchanges.spike.PriceStabilizer
import bot.trade.exchanges.spike.SpikeConfig
import bot.trade.exchanges.spike.SpikeDetector
import bot.trade.libs.*
import com.typesafe.config.Config
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import java.util.concurrent.LinkedBlockingDeque


class AlgorithmGrid(
    botSettings: BotSettings,
    exchangeBotsFiles: String,

    // activeOrdersService.getOrders(settings.name, settings.direction).toList().sortedBy { it.price }
    private val activeOrdersService: ActiveOrdersService,
    private val ordersService: OrderService? = null,

    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    api: String = conf.getString("api"),
    sec: String = conf.getString("sec"),
    client: Client = exchangeEnum.newClient(api, sec),
    logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null,
    isLog: Boolean = true,
    isEmulate: Boolean = false,
    sendMessage: (String, Boolean) -> Unit
) : Algorithm(
    botSettings = botSettings,
    exchangeBotsFiles = exchangeBotsFiles,
    queue = queue,
    exchangeEnum = exchangeEnum,
    conf = conf,
    api = api,
    sec = sec,
    client = client,
    isLog = isLog,
    isEmulate = isEmulate,
    logMessageQueue = logMessageQueue,
    sendMessage = sendMessage
) {
    private val settings: BotSettingsGrid = botSettings as BotSettingsGrid
    private val minRange = settings.parameters.tradingRange.lowerBound
    private val maxRange = settings.parameters.tradingRange.upperBound

    //private val orders: HashMap<String, Order> = HashMap()
    private val ordersListForRemove: MutableList<String> = mutableListOf()
    private val tradePair = botSettings.pair
    private val log = if (isLog) KotlinLogging.logger {} else null
    private val mainBalance = 0.0.toBigDecimal()
    private val balances: MutableMap<String, Balance> = HashMap()

    // Spike Aggregation
    private val spikeConfig: SpikeConfig = loadSpikeConfig()
    private val spikeDetector = SpikeDetector(spikeConfig)
    private val priceStabilizer = PriceStabilizer(spikeConfig)
    private val pendingSpikeOrders = mutableListOf<ActiveOrder>()
    private var spikeSide: SIDE? = null

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun setup() {}

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Trade -> {
                prevPrice = currentPrice

                currentPrice = msg.price

                from = if (from > msg.time) msg.time else from
                to = if (to < msg.time) msg.time else to

                when (settings.ordersType) {
                    TYPE.MARKET -> {
                        if (activeOrdersService.count(settings.name) < settings.parameters.orderMaxQuantity) {
                            activeOrdersService.saveOrder(
                                ActiveOrder(
                                    botName = settings.name,
                                    order = sendOrder(
//                                            amount = calcAmount(settings.parameters.orderQuantity, currentPrice),
                                        amount = settings.parameters.orderQuantity.value,
                                        orderSide = if (settings.direction == DIRECTION.LONG) SIDE.BUY
                                        else SIDE.SELL,
                                        orderType = settings.ordersType,
                                        price = BigDecimal(0.0),
                                        positionSide = settings.direction
                                    ).also {
                                        if (settings.direction == DIRECTION.LONG)
                                            it.lastBorderPrice = BigDecimal.ZERO
                                        else
                                            it.lastBorderPrice = BigDecimal(99999999999999L)
                                    })
                            )
                        }
                    }

                    TYPE.LIMIT -> {
                        val orders = if (prevPrice > currentPrice)
                            getInRangeOrders(currentPrice, prevPrice, SIDE.BUY)
                        else if (prevPrice < currentPrice)
                            getInRangeOrders(prevPrice, currentPrice, SIDE.SELL)
                        else emptyList()

                        if (orders.isNotEmpty()) {
                            val threadId = Thread.currentThread().name
                            log("[$threadId] Trade trigger: Checking ${orders.size} orders in price range")

                            // Verify each order is actually FILLED on exchange before reversing
                            val verifiedOrders = orders.filter { order ->
                                val orderId = order.orderId
                                if (orderId == null) {
                                    log("[$threadId] Trade trigger: Order ${order.id} has no orderId, skipping")
                                    return@filter false
                                }

                                try {
                                    val exchangeOrder = client.getOrder(settings.pair, orderId)
                                    val status = exchangeOrder?.status

                                    when (status) {
                                        STATUS.FILLED -> {
                                            log("[$threadId] Trade trigger: Order $orderId is FILLED, will reverse")
                                            true
                                        }
                                        STATUS.NEW, STATUS.PARTIALLY_FILLED -> {
                                            log("[$threadId] Trade trigger: Order $orderId still active (status=$status), skipping")
                                            false
                                        }
                                        else -> {
                                            log("[$threadId] Trade trigger: Order $orderId has status $status, skipping")
                                            false
                                        }
                                    }
                                } catch (e: Exception) {
                                    log("[$threadId] Trade trigger: Failed to check order $orderId status: ${e.message}, skipping")
                                    false
                                }
                            }

                            if (verifiedOrders.isNotEmpty()) {
                                log("[$threadId] Trade trigger: ${verifiedOrders.size} orders verified as FILLED")

                                if (spikeConfig.enabled) {
                                    handleWithSpikeDetection(verifiedOrders)
                                } else {
                                    val newOrders = verifiedOrders.map { createNextOrder(it, it.orderSide!!.reverse()) }
                                    log("[$threadId] Trade trigger: Created ${newOrders.size} next orders (reversed side)")

                                    val exchangeOrders = sendOrders(newOrders)
                                    log("[$threadId] Trade trigger: Sent ${exchangeOrders.size} orders to exchange, got orderIds: ${exchangeOrders.map { it.orderId }}")

                                    val updatedOrders = activeOrdersService.updateOrdersById(exchangeOrders)
                                    log("[$threadId] Trade trigger: Updated orders in DB: $updatedOrders")
                                }
                            } else {
                                log("[$threadId] Trade trigger: No orders verified as FILLED after checking exchange")
                            }
                        }

                        // Check spike resolution on every Trade tick while spike is active
                        if (spikeDetector.isActive()) {
                            log("[${Thread.currentThread().name}] Spike active: recording price $currentPrice for stabilization check")
                            priceStabilizer.recordPrice(currentPrice)
                            checkSpikeResolution()
                        }
                    }

                    else -> throw UnsupportedOrderTypeException("Unsupported order type: ${settings.ordersType}")
                }

                if (currentPrice !in minRange..maxRange) {
                    log("Price ${currentPrice.toPrice()}, not in range: ${settings.parameters.tradingRange}")
                    // TODO add chat message every time for example every hour
                }
            }

            is Balance -> balances[msg.asset] = msg

            is Order -> {
                log("${settings.name} Socket order update (STATUS = ${msg.status}): $msg")
                if (msg.pair == settings.pair) {
                    when (msg.status) {
                        STATUS.FILLED -> {
                            if (msg.type == TYPE.LIMIT) {
                                val threadId = Thread.currentThread().name

                                // If spike is active, Trade trigger handles everything
                                if (spikeDetector.isActive()) {
                                    log("[$threadId] WebSocket FILLED: spike active, skipping (handled by Trade trigger)")
                                    return
                                }

                                // UserTrade events from Gate.io fire on EVERY partial fill,
                                // but we must only create counter-orders when the order is FULLY filled.
                                // Verify actual order status on exchange before proceeding.
                                val exchangeOrder = try {
                                    client.getOrder(settings.pair, msg.orderId)
                                } catch (e: Exception) {
                                    log("[$threadId] WebSocket FILLED trigger: Failed to verify order ${msg.orderId} on exchange: ${e.message}, skipping")
                                    null
                                }

                                when (exchangeOrder?.status) {
                                    STATUS.FILLED -> {
                                        log("[$threadId] WebSocket FILLED trigger: Order ${msg.orderId} confirmed FILLED on exchange")
                                    }
                                    STATUS.PARTIALLY_FILLED -> {
                                        log("[$threadId] WebSocket FILLED trigger: Order ${msg.orderId} is PARTIALLY_FILLED (not fully filled yet), skipping")
                                        return
                                    }
                                    STATUS.NEW -> {
                                        log("[$threadId] WebSocket FILLED trigger: Order ${msg.orderId} still NEW on exchange, skipping")
                                        return
                                    }
                                    else -> {
                                        log("[$threadId] WebSocket FILLED trigger: Order ${msg.orderId} has status ${exchangeOrder?.status}, skipping")
                                        return
                                    }
                                }

                                activeOrdersService.getOrderByOrderId(settings.name, msg.orderId)?.let { dbOrder ->
                                    // Check if order side matches the filled order side
                                    // If sides don't match, the order was already processed by Trade trigger
                                    if (dbOrder.orderSide != msg.side) {
                                        log("[$threadId] WebSocket FILLED trigger: Order ${msg.orderId} already processed (DB side=${dbOrder.orderSide}, filled side=${msg.side}), skipping")
                                        return@let
                                    }

                                    val reversedSide = dbOrder.orderSide!!.reverse()

                                    // Check if there's already an order with reversed side at this price level
                                    // This prevents duplicates when Trade trigger already processed this level
                                    val existingReversedOrder = activeOrdersService.getOrderByPrice(
                                        settings.name, settings.direction, dbOrder.price!!
                                    )
                                    if (existingReversedOrder != null && existingReversedOrder.orderSide == reversedSide && existingReversedOrder.id != dbOrder.id) {
                                        log("[$threadId] WebSocket FILLED trigger: Order at price ${dbOrder.price} already has reversed order (id=${existingReversedOrder.id}, side=${existingReversedOrder.orderSide}), skipping to prevent duplicate")
                                        return@let
                                    }

                                    log("[$threadId] WebSocket FILLED trigger: Order for update: $dbOrder")
                                    val newOrder = createNextOrder(dbOrder, reversedSide)
                                    log("[$threadId] WebSocket FILLED trigger: Created next order (reversed side)")

                                    val exchangeOrders = sendOrders(listOf(newOrder))
                                    log("[$threadId] WebSocket FILLED trigger: Sent order to exchange, got orderId: ${exchangeOrders.firstOrNull()?.orderId}")

                                    val updatedOrder = activeOrdersService.updateOrdersById(exchangeOrders)
                                    log("[$threadId] WebSocket FILLED trigger: Updated order in DB: $updatedOrder")
                                } ?: log("[$threadId] WebSocket FILLED trigger: Order ${msg.orderId} not found in DB (already processed?)")
                            }
                        }

                        STATUS.NEW -> log("${settings.name} NEW order: $msg")
                        STATUS.PARTIALLY_FILLED -> log("${settings.name} PARTIALLY_FILLED order: $msg")
                        STATUS.CANCELED, STATUS.REJECTED -> {
                            log("Order inactive: $msg")
                        }

                        else -> log("${settings.name} Unsupported order status: ${msg.status}")
                    }
                }
            }

            is BotEvent -> {
                when (msg.type) {
                    BotEvent.Type.INTERRUPT -> {
                        log("${settings.name} received INTERRUPT signal, stopping...")
                        stopThis("INTERRUPT signal received")
                    }
                    BotEvent.Type.PAUSE -> {
                        log("${settings.name} received PAUSE signal (not implemented)")
                    }
                    else -> log("${settings.name} Unsupported BotEvent type: ${msg.type}")
                }
            }

            else -> log("Unsupported message: $msg")
        }
    }

    override fun synchronizeOrders() {
        val dbOrders = activeOrdersService.getOrders(settings.name, settings.direction).toList()

        if (dbOrders.isEmpty()) {
            log("Sync - No orders in DB, nothing to synchronize")
            return
        }

        // Check if this is a fresh start (all orders have null orderId)
        // Fresh start = StartBot was called, which creates new orders without orderIds
        val isFreshStart = dbOrders.all { it.orderId == null }

        if (isFreshStart) {
            log("Fresh start detected - ${dbOrders.size} new orders to send to exchange")
            sendMessage("🚀 Starting new grid with ${dbOrders.size} orders...", false)

            // For fresh start, just send all orders to exchange
            val ordersFromExchange = sendOrders(dbOrders, true)
            val updatedOrders = activeOrdersService.updateOrdersById(ordersFromExchange)
            log("Fresh start - sent ${ordersFromExchange.size} orders, updated in DB: ${updatedOrders.count()}")
            sendMessage("✅ Grid started: ${ordersFromExchange.size} orders placed on exchange", false)
            return
        }

        // Resume mode: synchronize existing orders
        log("Resume mode - synchronizing existing orders")

        val openOrders = client.getOpenOrders(settings.pair)
        val dbOrderIds = dbOrders.mapNotNull { it.orderId }.toSet()

        log("Sync - DB orders: ${dbOrders.size}, Exchange orders parsed: ${openOrders.size}")

        openOrders.forEach { order ->
            if (!dbOrderIds.contains(order.orderId)) {
                cancelOrder(settings.pair, order)
                log("Sync - cancel order: $order")
            } else {
                log("Sync - order is synchronized: $order")
            }
        }

        val openOrderIds = openOrders.map { it.orderId }.toSet()
        val newOrders = dbOrders.filter { !openOrderIds.contains(it.orderId) }

        if (newOrders.isNotEmpty()) {
            log("Sync - ${newOrders.size} orders from DB not found in exchange open orders list")

            // Safety check: if too many orders are "missing", likely a parsing issue
            // Don't create new orders if more than 30% of DB orders are "missing"
            val missingRatio = newOrders.size.toDouble() / dbOrders.size.toDouble()
            if (missingRatio > 0.3 && newOrders.size > 10) {
                log("WARNING: Too many orders missing from exchange (${newOrders.size}/${dbOrders.size} = ${(missingRatio * 100).toInt()}%). " +
                    "This might indicate a parsing issue. Skipping order creation to prevent duplicates.")
                sendMessage("⚠️ Sync warning: ${newOrders.size} orders not found on exchange. " +
                    "Possible API parsing issue. Please check manually.", false)
                return
            }

            // Verify each order status individually before recreating
            val ordersForExchange = newOrders.mapNotNull { exchangeOrder ->
                val orderStatus = exchangeOrder.orderId?.let {
                    try {
                        client.getOrder(settings.pair, it)?.status
                    } catch (e: Exception) {
                        log("Sync - Failed to get order status for ${it}: ${e.message}")
                        null
                    }
                }

                when (orderStatus) {
                    STATUS.FILLED -> {
                        log("Sync - Order filled: $exchangeOrder")
                        createNextOrder(exchangeOrder, exchangeOrder.orderSide!!.reverse())
                    }
                    STATUS.NEW, STATUS.PARTIALLY_FILLED -> {
                        // Order exists on exchange but wasn't in parsed list - skip to avoid duplicate
                        log("Sync - Order ${exchangeOrder.orderId} still active on exchange (status=$orderStatus), skipping")
                        null
                    }
                    STATUS.CANCELED, STATUS.REJECTED -> {
                        log("Sync - Order ${exchangeOrder.orderId} was canceled/rejected, recreating")
                        if (currentPrice >= exchangeOrder.price!!)
                            when (exchangeOrder.orderSide) {
                                SIDE.BUY -> exchangeOrder
                                SIDE.SELL -> createNextOrder(exchangeOrder, exchangeOrder.orderSide.reverse())
                                else -> throw UnsupportedOrderSideException()
                            }
                        else
                            when (exchangeOrder.orderSide) {
                                SIDE.BUY -> createNextOrder(exchangeOrder, exchangeOrder.orderSide.reverse())
                                SIDE.SELL -> exchangeOrder
                                else -> throw UnsupportedOrderSideException()
                            }
                    }
                    null -> {
                        // Order not found on exchange at all - recreate
                        log("Sync - Order ${exchangeOrder.orderId} not found on exchange, recreating")
                        if (currentPrice >= exchangeOrder.price!!)
                            when (exchangeOrder.orderSide) {
                                SIDE.BUY -> exchangeOrder
                                SIDE.SELL -> createNextOrder(exchangeOrder, exchangeOrder.orderSide.reverse())
                                else -> throw UnsupportedOrderSideException()
                            }
                        else
                            when (exchangeOrder.orderSide) {
                                SIDE.BUY -> createNextOrder(exchangeOrder, exchangeOrder.orderSide.reverse())
                                SIDE.SELL -> exchangeOrder
                                else -> throw UnsupportedOrderSideException()
                            }
                    }
                    else -> {
                        log("Sync - Unexpected order status: $orderStatus for ${exchangeOrder.orderId}")
                        null
                    }
                }
            }

            if (ordersForExchange.isNotEmpty()) {
                log("Sync - Sending ${ordersForExchange.size} orders to exchange")
                val ordersFromExchange = sendOrders(ordersForExchange, true)
                val updatedOrders = activeOrdersService.updateOrdersById(ordersFromExchange)
                log("Sync - sent orders: $updatedOrders")
            } else {
                log("Sync - No orders to send after verification")
            }
        }
    }

    private fun sendOrders(orders: Iterable<ActiveOrder>, isOrderFromSyncOrders: Boolean = false): List<ActiveOrder> {
        val threadId = Thread.currentThread().name
        val validOrders =
            orders.filter { it.price != null && it.stopPrice != null && it.amount != null && it.orderSide != null }
        
        log("[$threadId] sendOrders: preparing ${validOrders.size} valid orders (from ${orders.count()} input orders)")

        val sendOrders = validOrders.map { activeOrder ->
            SendOrder(
                price = when (settings.direction) {
                    DIRECTION.LONG -> when (activeOrder.orderSide) {
                        SIDE.BUY -> activeOrder.price!!
                        SIDE.SELL -> activeOrder.stopPrice!!
                        else -> throw UnsupportedOrderSideException()
                    }

                    DIRECTION.SHORT -> when (activeOrder.orderSide) {
                        SIDE.SELL -> activeOrder.price!!
                        SIDE.BUY -> activeOrder.stopPrice!!
                        else -> throw UnsupportedOrderSideException()
                    }
                },
                amount = activeOrder.amount!!,
                orderSide = activeOrder.orderSide,
                type = TYPE.LIMIT
            )
        }

        if (sendOrders.isNotEmpty()) {
            log("[$threadId] sendOrders: sending ${sendOrders.size} orders to exchange")
            val exchangeOrders =
                super.sendOrders(*sendOrders.toTypedArray(), isOrderFromSyncOrders = isOrderFromSyncOrders)
            log("[$threadId] sendOrders: received ${exchangeOrders.size} responses from exchange")
            
            // Check for duplicate orderIds from exchange (should never happen, but defensive)
            val orderIdCounts = exchangeOrders.mapNotNull { it.orderId }.groupingBy { it }.eachCount()
            val duplicates = orderIdCounts.filter { it.value > 1 }
            if (duplicates.isNotEmpty()) {
                log("[$threadId] WARNING: Exchange returned duplicate orderIds: $duplicates")
            }
            
            return validOrders.zip(exchangeOrders).map { (originalOrder, exchangeOrder) ->
                ActiveOrder(
                    id = originalOrder.id,
                    botName = originalOrder.botName,
                    orderId = exchangeOrder.orderId,
                    tradePair = originalOrder.tradePair,
                    amount = originalOrder.amount,
                    orderSide = exchangeOrder.side,
                    price = originalOrder.price,
                    stopPrice = originalOrder.stopPrice,
                    lastBorderPrice = originalOrder.lastBorderPrice,
                    direction = originalOrder.direction
                )
            }
        }
        return emptyList()
    }

    private fun getInRangeOrders(fromPrice: BigDecimal, toPrice: BigDecimal, side: SIDE): List<ActiveOrder> =
        when (settings.direction) {
            DIRECTION.LONG ->
                when (side) {
                    SIDE.BUY -> activeOrdersService.getOrderByPriceAndSideBetween(
                        botName = settings.name,
                        direction = settings.direction,
                        minPrice = fromPrice,
                        maxPrice = toPrice,
                        side = side
                    )

                    SIDE.SELL -> activeOrdersService.getOrderByStopPriceAndSideBetween(
                        botName = settings.name,
                        direction = settings.direction,
                        minStopPrice = fromPrice,
                        maxStopPrice = toPrice,
                        side = side
                    )

                    else -> throw UnsupportedOrderSideException()
                }

            DIRECTION.SHORT ->
                when (side) {
                    SIDE.BUY -> activeOrdersService.getOrderByStopPriceAndSideBetween(
                        botName = settings.name,
                        direction = settings.direction,
                        minStopPrice = fromPrice,
                        maxStopPrice = toPrice,
                        side = side
                    )

                    SIDE.SELL -> activeOrdersService.getOrderByPriceAndSideBetween(
                        botName = settings.name,
                        direction = settings.direction,
                        minPrice = fromPrice,
                        maxPrice = toPrice,
                        side = side
                    )

                    else -> throw UnsupportedOrderSideException()
                }
        }.toList()


    fun createNextOrder(prevOrder: ActiveOrder, side: SIDE): ActiveOrder = ActiveOrder(
        id = prevOrder.id!!,
        tradePair = prevOrder.tradePair,
        amount = prevOrder.amount,
        orderSide = side,
        price = prevOrder.price!!,
        stopPrice = prevOrder.stopPrice!!,
        lastBorderPrice = null,
        direction = settings.direction,
        botName = settings.name
    )

    // --- Spike Aggregation ---

    private fun loadSpikeConfig(): SpikeConfig = try {
        val confFile = "$path/spike_aggregation.conf"
        val config = readConf(confFile)
        if (config != null) {
            val loaded = SpikeConfig.fromConfig(config)
            log("Spike aggregation config loaded: enabled=${loaded.enabled}, threshold=${loaded.threshold}, " +
                "timeWindow=${loaded.timeWindowMs}ms, stabilizationDelay=${loaded.stabilizationDelayMs}ms, " +
                "maxWait=${loaded.maxWaitTimeMs}ms, maxDrift=${loaded.maxPriceDriftPercent}%, " +
                "orderType=${loaded.aggregateOrderType}")
            loaded
        } else {
            log("Spike aggregation config not found at $confFile, feature disabled")
            SpikeConfig.disabled()
        }
    } catch (e: Exception) {
        log("Failed to load spike_aggregation.conf: ${e.message}, spike aggregation disabled")
        SpikeConfig.disabled()
    }

    private fun handleWithSpikeDetection(verifiedOrders: List<ActiveOrder>) {
        val threadId = Thread.currentThread().name
        log("[$threadId] Spike detection: processing ${verifiedOrders.size} verified orders at price $currentPrice")

        for (order in verifiedOrders) {
            val wasActiveBeforeRegister = spikeDetector.isActive()
            val isSpike = spikeDetector.registerFill(order, currentPrice)

            log("[$threadId] Spike detection: registerFill order id=${order.id}, orderId=${order.orderId}, " +
                "side=${order.orderSide}, price=${order.price}, amount=${order.amount}, " +
                "fillPrice=$currentPrice, isSpike=$isSpike, wasActive=$wasActiveBeforeRegister, " +
                "bufferedCount=${spikeDetector.getBufferedFills().size}")

            if (isSpike && !wasActiveBeforeRegister) {
                log("[$threadId] SPIKE DETECTED: ${spikeDetector.getBufferedFills().size} orders filled within ${spikeConfig.timeWindowMs}ms, " +
                    "spikeSide=${order.orderSide}, currentPrice=$currentPrice")
                sendMessage("Spike detected: ${spikeDetector.getBufferedFills().size} orders. Waiting for price stabilization...", false)
                spikeSide = order.orderSide
            }
        }

        if (spikeDetector.isActive()) {
            pendingSpikeOrders.addAll(verifiedOrders)
            log("[$threadId] Spike buffer: ${pendingSpikeOrders.size} orders pending, " +
                "total amount: ${spikeDetector.getTotalAmount()}, weightedAvgPrice: ${spikeDetector.getWeightedAvgPrice()}, " +
                "spikeSide=$spikeSide, added ${verifiedOrders.size} orders: [${verifiedOrders.joinToString { "id=${it.id},price=${it.price}" }}]")
        } else {
            // Normal mode: standard grid logic
            val newOrders = verifiedOrders.map { createNextOrder(it, it.orderSide!!.reverse()) }
            log("[$threadId] Trade trigger: Created ${newOrders.size} next orders (reversed side)")

            val exchangeOrders = sendOrders(newOrders)
            log("[$threadId] Trade trigger: Sent ${exchangeOrders.size} orders to exchange, got orderIds: ${exchangeOrders.map { it.orderId }}")

            val updatedOrders = activeOrdersService.updateOrdersById(exchangeOrders)
            log("[$threadId] Trade trigger: Updated orders in DB: $updatedOrders")
        }
    }

    private fun checkSpikeResolution() {
        val threadId = Thread.currentThread().name
        val spikeStart = spikeDetector.getSpikeStartTime() ?: return
        val elapsed = System.currentTimeMillis() - spikeStart

        // 1. Safety cutoff: price drifted further in spike direction
        val avgPrice = spikeDetector.getWeightedAvgPrice()
        var driftPercent = BigDecimal.ZERO
        if (avgPrice > BigDecimal.ZERO) {
            driftPercent = (currentPrice - avgPrice).abs()
                .divide(avgPrice, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))

            val isDriftExceeded = when (spikeSide) {
                SIDE.SELL -> currentPrice > avgPrice && driftPercent > spikeConfig.maxPriceDriftPercent
                SIDE.BUY -> currentPrice < avgPrice && driftPercent > spikeConfig.maxPriceDriftPercent
                else -> false
            }

            if (isDriftExceeded) {
                log("[$threadId] Spike SAFETY CUTOFF: price=$currentPrice drifted ${driftPercent}% from avgPrice=$avgPrice, " +
                    "maxDrift=${spikeConfig.maxPriceDriftPercent}%, spikeSide=$spikeSide, elapsed=${elapsed}ms")
                sendMessage("Spike safety cutoff! Price drift exceeded ${spikeConfig.maxPriceDriftPercent}%. Executing immediate counter-orders.", false)
                executeSpikeCounterOrders(useMarketPrice = true)
                return
            }
        }

        // 2. Timeout
        if (priceStabilizer.isTimedOut(spikeStart)) {
            log("[$threadId] Spike TIMEOUT: elapsed=${elapsed}ms, maxWait=${spikeConfig.maxWaitTimeMs}ms, " +
                "currentPrice=$currentPrice, avgPrice=$avgPrice, drift=${driftPercent}%")
            sendMessage("Spike timeout reached. Executing counter-orders at current price.", false)
            executeSpikeCounterOrders(useMarketPrice = true)
            return
        }

        // 3. Price stabilized
        val isStable = priceStabilizer.isStable(spikeStart)
        if (isStable) {
            val medianPrice = priceStabilizer.getMedianPrice()
            log("[$threadId] Spike STABILIZED: medianPrice=$medianPrice, currentPrice=$currentPrice, " +
                "avgPrice=$avgPrice, elapsed=${elapsed}ms, pendingOrders=${pendingSpikeOrders.size}")
            sendMessage("Price stabilized at $medianPrice. Executing aggregated counter-order.", false)
            executeSpikeCounterOrders(useMarketPrice = false)
            return
        }

        // Not resolved yet — log monitoring status
        log("[$threadId] Spike monitoring: elapsed=${elapsed}ms/${spikeConfig.maxWaitTimeMs}ms, " +
            "currentPrice=$currentPrice, avgPrice=$avgPrice, drift=${driftPercent}%, " +
            "isStable=$isStable, pendingOrders=${pendingSpikeOrders.size}, spikeSide=$spikeSide")
    }

    private fun executeSpikeCounterOrders(useMarketPrice: Boolean) {
        val threadId = Thread.currentThread().name
        val reversedSide = spikeSide!!.reverse()

        val totalAmount = pendingSpikeOrders
            .mapNotNull { it.amount }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        log("[$threadId] Spike execute: useMarketPrice=$useMarketPrice, reversedSide=$reversedSide, " +
            "totalAmount=$totalAmount, pendingOrders=${pendingSpikeOrders.size}, " +
            "orders=[${pendingSpikeOrders.joinToString { "id=${it.id},orderId=${it.orderId},side=${it.orderSide},price=${it.price},amount=${it.amount}" }}]")

        val orderPrice = if (useMarketPrice || spikeConfig.aggregateOrderType == "MARKET") {
            log("[$threadId] Spike execute: using market price $currentPrice (useMarketPrice=$useMarketPrice, configType=${spikeConfig.aggregateOrderType})")
            currentPrice
        } else {
            val median = priceStabilizer.getMedianPrice()
            val offset = median.multiply(spikeConfig.limitPriceOffsetPercent)
                .divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
            val limitPrice = when (reversedSide) {
                SIDE.BUY -> median - offset
                SIDE.SELL -> median + offset
                else -> median
            }
            log("[$threadId] Spike execute: using limit price $limitPrice (median=$median, offset=$offset, " +
                "offsetPercent=${spikeConfig.limitPriceOffsetPercent}%, reversedSide=$reversedSide)")
            limitPrice
        }

        val orderType = if (useMarketPrice) TYPE.MARKET else TYPE.LIMIT
        log("[$threadId] Spike aggregated order: $reversedSide $totalAmount @ $orderPrice, type=$orderType")

        try {
            val result = sendOrder(
                price = orderPrice,
                amount = totalAmount,
                orderSide = reversedSide,
                orderType = orderType
            )
            log("[$threadId] Spike aggregated order sent: orderId=${result.orderId}, status=${result.status}, " +
                "executedQty=${result.executedQty}, fee=${result.fee}")

            redistributeGridAfterSpike(pendingSpikeOrders, reversedSide)

            val avgSpikePrice = spikeDetector.getWeightedAvgPrice()
            val profit = when (reversedSide) {
                SIDE.BUY -> (avgSpikePrice - orderPrice) * totalAmount
                SIDE.SELL -> (orderPrice - avgSpikePrice) * totalAmount
                else -> BigDecimal.ZERO
            }
            log("[$threadId] Spike resolved: reversedSide=$reversedSide, totalAmount=$totalAmount, " +
                "orderPrice=$orderPrice, avgSpikePrice=$avgSpikePrice, estimatedProfit=${profit.setScale(4, RoundingMode.HALF_UP)}")
            sendMessage(
                "Spike resolved: $reversedSide $totalAmount @ $orderPrice " +
                    "(avg spike price: $avgSpikePrice, est. profit: ${profit.setScale(4, RoundingMode.HALF_UP)} USDT)", false
            )
        } catch (e: Exception) {
            log("[$threadId] Spike aggregated order FAILED: ${e.message}, " +
                "pendingOrders=${pendingSpikeOrders.size}, totalAmount=$totalAmount, orderPrice=$orderPrice")
            log("[$threadId] Falling back to standard grid counter-orders for ${pendingSpikeOrders.size} orders")
            sendMessage("Spike aggregated order failed: ${e.message}. Falling back to grid orders.", false)
            fallbackToGridCounterOrders()
        } finally {
            log("[$threadId] Spike state reset: clearing ${pendingSpikeOrders.size} pending orders, spikeSide=$spikeSide")
            spikeDetector.reset()
            priceStabilizer.reset()
            pendingSpikeOrders.clear()
            spikeSide = null
        }
    }

    private fun redistributeGridAfterSpike(filledOrders: List<ActiveOrder>, newSide: SIDE) {
        val threadId = Thread.currentThread().name

        log("[$threadId] Redistributing grid: ${filledOrders.size} filled orders -> newSide=$newSide, " +
            "orders=[${filledOrders.joinToString { "id=${it.id},price=${it.price},stopPrice=${it.stopPrice}" }}]")

        val newOrders = filledOrders.map { order ->
            createNextOrder(order, newSide)
        }

        log("[$threadId] Redistributing grid: sending ${newOrders.size} orders to exchange")

        val exchangeOrders = sendOrders(newOrders)
        log("[$threadId] Grid redistributed: ${exchangeOrders.size} orders placed, " +
            "orderIds=[${exchangeOrders.joinToString { "id=${it.id},orderId=${it.orderId},side=${it.orderSide},price=${it.price}" }}]")

        val updatedOrders = activeOrdersService.updateOrdersById(exchangeOrders)
        log("[$threadId] Grid redistribution DB update: ${updatedOrders.count()} orders updated")
    }

    private fun fallbackToGridCounterOrders() {
        val threadId = Thread.currentThread().name
        log("[$threadId] Spike FALLBACK: creating standard grid counter-orders for ${pendingSpikeOrders.size} pending orders")

        val newOrders = pendingSpikeOrders.map {
            log("[$threadId] Spike FALLBACK: order id=${it.id}, price=${it.price}, side=${it.orderSide} -> reversed to ${it.orderSide!!.reverse()}")
            createNextOrder(it, it.orderSide.reverse())
        }

        log("[$threadId] Spike FALLBACK: sending ${newOrders.size} counter-orders to exchange")
        val exchangeOrders = sendOrders(newOrders)
        log("[$threadId] Spike FALLBACK: sent ${exchangeOrders.size} orders, " +
            "orderIds=[${exchangeOrders.joinToString { "orderId=${it.orderId}" }}]")

        val updatedOrders = activeOrdersService.updateOrdersById(exchangeOrders)
        log("[$threadId] Spike FALLBACK: DB update complete, ${updatedOrders.count()} orders updated")
    }
}