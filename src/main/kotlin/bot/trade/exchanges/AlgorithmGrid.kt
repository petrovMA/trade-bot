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

    // Force sync flag: set true via FORCE_SYNC event to bypass the missing-orders safety check
    @Volatile private var forceSyncEnabled = false

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

                                    // Spike detection: register fill and check if spike is active
                                    if (spikeConfig.enabled) {
                                        spikeDetector.registerFill(dbOrder, msg.price ?: currentPrice)
                                        if (spikeDetector.isActive()) {
                                            // Spike detected or already active — buffer order, don't create counter
                                            if (pendingSpikeOrders.none { it.orderId == dbOrder.orderId }) {
                                                pendingSpikeOrders.add(dbOrder)
                                                if (spikeSide == null) spikeSide = dbOrder.orderSide
                                                log("[$threadId] WebSocket FILLED trigger: Spike active, buffered order ${msg.orderId} (pending=${pendingSpikeOrders.size})")
                                                sendMessage("Spike detected: ${spikeDetector.getBufferedFills().size} orders. Waiting for price stabilization...", false)
                                            } else {
                                                log("[$threadId] WebSocket FILLED trigger: Spike active, order ${msg.orderId} already buffered, skipping")
                                            }
                                            return@let
                                        }
                                    }

                                    val reversedSide = dbOrder.orderSide!!.reverse()

                                                    // Check if there's already an order with reversed side at this price level
                                    // This prevents duplicates when Trade trigger already processed this level.
                                    // Note: createNextOrder reuses the same DB id, so we do NOT check id equality —
                                    // the orderSide check already ensures we're not matching the order against itself.
                                    val existingReversedOrder = activeOrdersService.getOrderByPrice(
                                        settings.name, settings.direction, dbOrder.price!!
                                    )
                                    if (existingReversedOrder != null && existingReversedOrder.orderSide == reversedSide) {
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
                    BotEvent.Type.FORCE_SYNC -> {
                        log("${settings.name} received FORCE_SYNC signal, bypassing safety check")
                        sendMessage("🔄 Force sync started for ${settings.name}...", false)
                        forceSyncEnabled = true
                        synchronizeOrders()
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

        // Remove duplicate orders at the same price level (keep the one with the latest orderId)
        val duplicatesByPrice = dbOrders.filter { it.price != null && it.orderSide != null }
            .groupBy { Pair(it.price, it.orderSide) }
            .filter { it.value.size > 1 }

        if (duplicatesByPrice.isNotEmpty()) {
            log("Sync - Found ${duplicatesByPrice.size} price levels with duplicate orders")
            duplicatesByPrice.forEach { (key, orders) ->
                val (price, side) = key
                // Keep the first order (oldest), cancel the rest
                val toKeep = orders.first()
                val toRemove = orders.drop(1)
                toRemove.forEach { duplicate ->
                    log("Sync - Removing duplicate order at price=$price side=$side: orderId=${duplicate.orderId} (keeping orderId=${toKeep.orderId})")
                    duplicate.orderId?.let { orderId ->
                        try {
                            client.cancelOrder(settings.pair, orderId)
                        } catch (e: Exception) {
                            log("Sync - Failed to cancel duplicate order $orderId: ${e.message}")
                        }
                        activeOrdersService.deleteByOrderId(orderId)
                    }
                }
            }
            sendMessage("🔄 Sync: removed ${duplicatesByPrice.values.sumOf { it.size - 1 }} duplicate orders", false)
        }

        // Re-read orders after dedup
        val cleanDbOrders = if (duplicatesByPrice.isNotEmpty())
            activeOrdersService.getOrders(settings.name, settings.direction).toList()
        else dbOrders

        val openOrders = client.getOpenOrders(settings.pair)
        val dbOrderIds = cleanDbOrders.mapNotNull { it.orderId }.toSet()

        log("Sync - DB orders: ${cleanDbOrders.size}, Exchange orders parsed: ${openOrders.size}")

        openOrders.forEach { order ->
            if (!dbOrderIds.contains(order.orderId)) {
                cancelOrder(settings.pair, order)
                log("Sync - cancel order: $order")
            } else {
                log("Sync - order is synchronized: $order")
            }
        }

        val openOrderIds = openOrders.map { it.orderId }.toSet()
        val newOrders = cleanDbOrders.filter { !openOrderIds.contains(it.orderId) }

        if (newOrders.isNotEmpty()) {
            log("Sync - ${newOrders.size} orders from DB not found in exchange open orders list")

            // Safety check: if too many orders are "missing", likely a parsing issue.
            // Don't create new orders if more than 30% of DB orders are "missing".
            // Can be bypassed via /forcesync command (e.g. after a spike left many holes in the grid).
            val missingRatio = newOrders.size.toDouble() / cleanDbOrders.size.toDouble()
            if (missingRatio > 0.3 && newOrders.size > 10) {
                if (forceSyncEnabled) {
                    log("Force sync active — bypassing safety check (${newOrders.size}/${cleanDbOrders.size} = ${(missingRatio * 100).toInt()}% missing)")
                    sendMessage("⚡ Force sync: bypassing safety check for ${newOrders.size} missing orders.", false)
                    forceSyncEnabled = false
                } else {
                    log("WARNING: Too many orders missing from exchange (${newOrders.size}/${cleanDbOrders.size} = ${(missingRatio * 100).toInt()}%). " +
                        "This might indicate a parsing issue. Skipping order creation to prevent duplicates.")
                    sendMessage(
                        "⚠️ Sync warning: ${newOrders.size} orders not found on exchange. " +
                            "Possible API parsing issue. Please check manually.\n" +
                            "Use /forcesync ${settings.name} to force sync anyway.", false
                    )
                    return
                }
            } else {
                // Reset force flag if it was set but threshold wasn't reached
                forceSyncEnabled = false
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
                // Try batch first; if it fails, send individually so partial failures don't block full recovery.
                // This handles post-spike scenarios where some counter-order prices needed adjustment.
                try {
                    val ordersFromExchange = sendOrders(ordersForExchange, true)
                    // Restore original grid prices for any adjusted orders (price fields from ordersForExchange)
                    val dbUpdates = ordersFromExchange.zip(ordersForExchange).map { (result, original) ->
                        result.copy(price = original.price, stopPrice = original.stopPrice)
                    }
                    val updatedOrders = activeOrdersService.updateOrdersById(dbUpdates)
                    log("Sync - sent orders: $updatedOrders")
                } catch (e: Exception) {
                    log("Sync - Batch send failed (${e.message}), retrying individually")
                    sendMessage("⚠️ Sync batch failed, retrying orders one by one...", false)
                    var syncOk = 0; var syncFail = 0
                    ordersForExchange.forEach { order ->
                        // For LONG BUY counters: if grid slot price is above current market,
                        // send at current market price to avoid price-band rejection.
                        // Also increase amount if needed to meet the exchange minimum notional.
                        val sendOrder = when {
                            settings.direction == DIRECTION.LONG && order.orderSide == SIDE.BUY &&
                                    currentPrice > BigDecimal.ZERO && order.price != null && order.price > currentPrice -> {
                                val adjustedAmount = adjustAmountForMinNotional(order.amount!!, currentPrice)
                                log("Sync - Individual retry: BUY price ${order.price} > market $currentPrice → using currentPrice" +
                                    if (adjustedAmount != order.amount) ", amount ${order.amount} → $adjustedAmount (min notional)" else "")
                                order.copy(price = currentPrice, amount = adjustedAmount)
                            }
                            else -> order
                        }
                        try {
                            val result = sendOrders(listOf(sendOrder), true)
                            if (result.isNotEmpty()) {
                                // Restore original grid price in DB
                                val dbUpdate = result.first().copy(price = order.price, stopPrice = order.stopPrice)
                                activeOrdersService.updateOrdersById(listOf(dbUpdate))
                                syncOk++
                            }
                        } catch (e2: Exception) {
                            syncFail++
                            log("Sync - Individual send failed for id=${order.id}: ${e2.message}")
                        }
                    }
                    log("Sync - Individual retry done: $syncOk/${ordersForExchange.size} placed, $syncFail failed")
                    sendMessage("Sync recovery: $syncOk/${ordersForExchange.size} orders placed", false)
                }
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

    /**
     * If [amount] × [sendPrice] < [BotSettingsGrid.Parameters.minNotionalUsdt], increases
     * the amount to meet the exchange minimum notional. Returns the (possibly adjusted) amount
     * rounded up to the pair's amount precision.
     * No-op when minNotionalUsdt is null/zero or sendPrice is zero.
     */
    private fun adjustAmountForMinNotional(amount: BigDecimal, sendPrice: BigDecimal): BigDecimal {
        val minNotional = settings.parameters.minNotionalUsdt ?: return amount
        if (minNotional <= BigDecimal.ZERO || sendPrice <= BigDecimal.ZERO) return amount
        val notional = amount.multiply(sendPrice)
        if (notional >= minNotional) return amount
        val adjusted = minNotional.divide(sendPrice, settings.countOfDigitsAfterDotForAmount, RoundingMode.CEILING)
        log("Min notional: $amount × $sendPrice = $notional < $minNotional USDT minimum → amount adjusted to $adjusted")
        return adjusted
    }

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
            // Dedup: only add orders not already buffered (e.g. by WebSocket handler)
            val existingOrderIds = pendingSpikeOrders.mapNotNull { it.orderId }.toSet()
            val newOrders = verifiedOrders.filter { it.orderId !in existingOrderIds }
            pendingSpikeOrders.addAll(newOrders)
            log("[$threadId] Spike buffer: ${pendingSpikeOrders.size} orders pending (added ${newOrders.size}, skipped ${verifiedOrders.size - newOrders.size} duplicates), " +
                "total amount: ${spikeDetector.getTotalAmount()}, weightedAvgPrice: ${spikeDetector.getWeightedAvgPrice()}, " +
                "spikeSide=$spikeSide")
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

        // Filter out orders that already have counter-orders (sent before spike was detected).
        // Note: createNextOrder reuses the same DB id with reversed side, so we do NOT check
        // id equality — the orderSide check already ensures we match the correct reversed order.
        val ordersWithoutCounter = pendingSpikeOrders.filter { order ->
            val existingReversedOrder = activeOrdersService.getOrderByPrice(
                settings.name, settings.direction, order.price!!
            )
            val alreadyHasCounter = existingReversedOrder != null &&
                existingReversedOrder.orderSide == reversedSide
            if (alreadyHasCounter) {
                log("[$threadId] Spike execute: order id=${order.id} at price=${order.price} already has counter (id=${existingReversedOrder!!.id}), excluding from aggregation")
            }
            !alreadyHasCounter
        }

        if (ordersWithoutCounter.isEmpty()) {
            log("[$threadId] Spike execute: all ${pendingSpikeOrders.size} orders already have counters, nothing to aggregate")
            return
        }

        val totalAmount = ordersWithoutCounter
            .mapNotNull { it.amount }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        log("[$threadId] Spike execute: useMarketPrice=$useMarketPrice, reversedSide=$reversedSide, " +
            "totalAmount=$totalAmount, ordersToAggregate=${ordersWithoutCounter.size}/${pendingSpikeOrders.size}, " +
            "orders=[${ordersWithoutCounter.joinToString { "id=${it.id},orderId=${it.orderId},side=${it.orderSide},price=${it.price},amount=${it.amount}" }}]")

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

            redistributeGridAfterSpike(ordersWithoutCounter, reversedSide)

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

        // Filter out orders that already have counter-orders on exchange
        // (e.g. WebSocket handler or Trade trigger sent counter before spike was detected).
        // Note: createNextOrder reuses the same DB id with reversed side, so we do NOT check
        // id equality — the orderSide check already ensures we match the correct reversed order.
        val ordersToProcess = pendingSpikeOrders.filter { order ->
            val reversedSide = order.orderSide!!.reverse()
            val existingReversedOrder = activeOrdersService.getOrderByPrice(
                settings.name, settings.direction, order.price!!
            )
            val alreadyHasCounter = existingReversedOrder != null &&
                existingReversedOrder.orderSide == reversedSide
            if (alreadyHasCounter) {
                log("[$threadId] Spike FALLBACK: order id=${order.id} at price=${order.price} already has counter (id=${existingReversedOrder!!.id}), skipping")
            }
            !alreadyHasCounter
        }

        if (ordersToProcess.isEmpty()) {
            log("[$threadId] Spike FALLBACK: all ${pendingSpikeOrders.size} orders already have counters, nothing to do")
            return
        }

        // Send each counter-order individually to avoid one failure aborting the whole batch.
        // For BUY counter-orders where the grid slot price is above current market price
        // (common after a spike crashes back), we use currentPrice instead to avoid futures
        // exchange price-band rejection. The original DB price is restored after sending
        // to preserve the grid structure for future cycles.
        var successCount = 0
        var failCount = 0
        ordersToProcess.forEach { order ->
            val reversedSide = order.orderSide!!.reverse()
            val newOrder = createNextOrder(order, reversedSide)

            // For LONG BUY: if grid slot price is above current market, send at current price.
            // For LONG SELL: if grid slot stopPrice is below current market, send at current price.
            val sendPrice = when {
                settings.direction == DIRECTION.LONG && reversedSide == SIDE.BUY &&
                        currentPrice > BigDecimal.ZERO && newOrder.price!! > currentPrice ->
                    currentPrice.also {
                        log("[$threadId] Spike FALLBACK: grid BUY price ${newOrder.price} > market $currentPrice, sending at market price")
                    }
                settings.direction == DIRECTION.LONG && reversedSide == SIDE.SELL &&
                        currentPrice > BigDecimal.ZERO && newOrder.stopPrice!! < currentPrice ->
                    currentPrice.also {
                        log("[$threadId] Spike FALLBACK: grid SELL stopPrice ${newOrder.stopPrice} < market $currentPrice, sending at market price")
                    }
                else -> if (reversedSide == SIDE.BUY) newOrder.price!! else newOrder.stopPrice!!
            }

            // Build send order with adjusted price and (if needed) increased amount for min notional.
            val orderForSend = when {
                settings.direction == DIRECTION.LONG && reversedSide == SIDE.BUY -> {
                    val adjustedAmount = adjustAmountForMinNotional(newOrder.amount!!, sendPrice)
                    newOrder.copy(price = sendPrice, amount = adjustedAmount)
                }
                settings.direction == DIRECTION.LONG && reversedSide == SIDE.SELL -> newOrder.copy(stopPrice = sendPrice)
                else -> newOrder
            }

            log("[$threadId] Spike FALLBACK: order id=${order.id}, gridPrice=${newOrder.price}, sendPrice=$sendPrice, side=$reversedSide")
            try {
                val exchangeOrders = sendOrders(listOf(orderForSend))
                if (exchangeOrders.isNotEmpty()) {
                    // Restore original grid slot prices in DB to preserve grid structure
                    val dbUpdate = exchangeOrders.first().copy(
                        price = newOrder.price,
                        stopPrice = newOrder.stopPrice
                    )
                    activeOrdersService.updateOrdersById(listOf(dbUpdate))
                    successCount++
                    log("[$threadId] Spike FALLBACK: placed id=${order.id} orderId=${exchangeOrders.first().orderId}")
                }
            } catch (e: Exception) {
                failCount++
                log("[$threadId] Spike FALLBACK: failed for id=${order.id} price=$sendPrice: ${e.message}. DB left as-is for sync recovery on restart.")
            }
        }

        log("[$threadId] Spike FALLBACK done: $successCount/${ordersToProcess.size} succeeded, $failCount failed")
        if (failCount > 0) {
            sendMessage(
                "Spike fallback: $successCount/${ordersToProcess.size} counter-orders placed. " +
                    "$failCount order(s) will be recovered on next bot restart.", false
            )
        } else {
            sendMessage("Spike fallback: all $successCount counter-orders placed successfully.", false)
        }
    }
}