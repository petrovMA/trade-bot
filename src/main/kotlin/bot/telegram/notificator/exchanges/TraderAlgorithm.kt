package bot.telegram.notificator.exchanges

import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.clients.socket.SocketThread
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.absoluteValue


class TraderAlgorithm(
    private val botSettings: BotSettings,
    val queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    private val exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    private val conf: Config = getConfigByExchange(exchangeEnum)!!,
    private val api: String = conf.getString("api"),
    private val sec: String = conf.getString("sec"),
    private var client: Client = newClient(exchangeEnum, api, sec),
    isLog: Boolean = true,
    private val isEmulate: Boolean = false,
    val sendMessage: (String, Boolean) -> Unit
) : Thread() {
    private val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()
    private val firstBalanceForOrderAmount = botSettings.orderBalanceType == "first"
    private val minRange = botSettings.tradingRange.first
    private val maxRange = botSettings.tradingRange.second
    private val ordersListForRemove: MutableList<Pair<String, Order>> = mutableListOf()

    private val path: String = "exchangeBots/${botSettings.name}"

    private val log = if (isLog) KotlinLogging.logger {} else null

    private val waitTime = conf.getDuration("interval.wait_socket_time")
    private val formatAmount = "%.${botSettings.countOfDigitsAfterDotForAmount}f"
    private val formatPrice = "%.${botSettings.countOfDigitsAfterDotForPrice}f"
    private val retryGetOrderCount = conf.getInt("retry.get_order_count")
    private val retrySentOrderCount: Int = conf.getInt("retry.sent_order_count")
    private val retryGetOrderInterval = conf.getDuration("retry.get_order_interval")

    private var stopThread = false
    private var currentPrice: BigDecimal = 0.toBigDecimal()
    private var prevPrice: BigDecimal = 0.toBigDecimal()

    private val settingsPath = "$path/settings.json"

    private val ordersPath = "$path/orders"
    private val orders: MutableMap<String, Order> =
        if (isEmulate.not()) ObservableHashMap(
            filePath = ordersPath,
            keyToFileName = { key -> key.replace('.', '_') + ".json" },
            fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
        )
        else mutableMapOf()

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    private var socket: SocketThread = client.socket(botSettings.pair, interval, queue)

    fun interruptThis(msg: String? = null) {
        socket.interrupt()
        var msgErr = "#Interrupt_${botSettings.pair} Thread, socket.status = ${socket.state}"
        var logErr = "Thread for ${botSettings.pair} Interrupt, socket.status = ${socket.state}"
        msg?.let { msgErr = "$msgErr\nMessage: $it"; logErr = "$logErr\nMessage: $it"; }
        send(msgErr)
        log?.warn(logErr)
        stopThread = true
    }

    override fun run() {
        saveBotSettings(botSettings)
        stopThread = false
        try {
            if (File(ordersPath).isDirectory.not()) Files.createDirectories(Paths.get(ordersPath))

            synchronizeOrders()

            socket.run { start() }

            var msg = if (isEmulate) client.nextEvent() /* only for test */
            else queue.poll(waitTime)

            do {
                if (stopThread) return
                try {
                    when (msg) {
                        is Trade -> {
                            prevPrice = currentPrice
                            currentPrice = msg.price
                            log?.debug("${botSettings.name} TradeEvent:\n$msg")

                            from = if (from > msg.time) msg.time else from
                            to = if (to < msg.time) msg.time else to

                            if (currentPrice > minRange && currentPrice < maxRange) {

                                val priceIn = (currentPrice - (currentPrice % botSettings.orderDistance)).toPrice()

                                when (botSettings.ordersType) {
                                    TYPE.MARKET -> {
                                        orders[priceIn]?.let { log?.trace("${botSettings.name} Order already exist: $it") }
                                            ?: run {
                                                if (orders.size < botSettings.orderMaxQuantity) {
                                                    orders[priceIn] = sentOrder(
                                                        amount = botSettings.orderSize,
                                                        orderSide = if (botSettings.direction == DIRECTION.LONG) SIDE.BUY
                                                        else SIDE.SELL,
                                                        price = priceIn.toBigDecimal(),
                                                        orderType = TYPE.MARKET
                                                    ).also {
                                                        if (botSettings.direction == DIRECTION.LONG)
                                                            it.lastBorderPrice = BigDecimal.ZERO
                                                        else
                                                            it.lastBorderPrice = BigDecimal(99999999999999L)
                                                    }
                                                }
                                            }
                                    }

                                    TYPE.LIMIT -> {
                                        when (botSettings.direction) {
                                            DIRECTION.LONG -> {
                                                var keyPrice = priceIn.toBigDecimal()
                                                while (keyPrice > minRange) {
                                                    keyPrice.toPrice().let {
                                                        orders[it]?.let { order -> log?.trace("${botSettings.name} Order already exist: $order") }
                                                            ?: run {
                                                                if (orders.size < botSettings.orderMaxQuantity) {
                                                                    orders[it] = sentOrder(
                                                                        price = keyPrice,
                                                                        amount = botSettings.orderSize,
                                                                        orderSide = SIDE.BUY,
                                                                        orderType = TYPE.LIMIT
                                                                    )
                                                                } else
                                                                    log?.trace("${botSettings.name} Orders count limit reached: price = $keyPrice; orderMaxQuantity = ${botSettings.orderMaxQuantity}")
                                                            }
                                                    }
                                                    keyPrice -= botSettings.orderDistance
                                                }

                                                val prev =
                                                    (prevPrice - (prevPrice % botSettings.orderDistance)).toPrice()

                                                if (priceIn.toBigDecimal() != prev.toBigDecimal()) {
                                                    orders[prev]?.let { order ->
                                                        if (order.price?.let { it > currentPrice } == true && order.status != STATUS.FILLED && order.status != STATUS.REJECTED && order.type != TYPE.MARKET) {
                                                            val o = getOrder(botSettings.pair, order.orderId)
                                                                ?: order.apply { status = STATUS.FILLED }

                                                            if (o.status == STATUS.FILLED || o.status == STATUS.REJECTED) {
                                                                orders[prev] = o.also {
                                                                    it.type = TYPE.MARKET
                                                                    it.lastBorderPrice = BigDecimal.ZERO
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            DIRECTION.SHORT -> {
                                                TODO()
                                            }
                                        }
                                    }

                                    else -> throw UnsupportedOrderTypeException("Error: Unknown order type '${botSettings.ordersType}'!")
                                }
                            } else
                                log?.warn("${botSettings.name} Price ${format(currentPrice)}, not in range: ${botSettings.tradingRange}")

                            when (botSettings.direction) {
                                DIRECTION.LONG -> {
                                    val ordersForUpdate = orders.filter { it.value.type == TYPE.MARKET }

                                    ordersForUpdate.forEach { (k, v) ->
                                        if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                                            v.lastBorderPrice = currentPrice

                                            if (
                                                v.stopPrice?.run { this < currentPrice - botSettings.triggerDistance } == true
                                                || v.stopPrice == null && k.toBigDecimal() < (currentPrice - botSettings.triggerDistance - botSettings.enableStopOrderDistance)
                                            ) {
                                                v.stopPrice = currentPrice - botSettings.triggerDistance
                                            }

                                            orders[k] = v
                                        }
                                        if (v.stopPrice?.run { this >= currentPrice } == true) {
                                            log?.debug("${botSettings.name} Order close: $v")
                                            ordersListForRemove.add(k to v.apply { side = SIDE.SELL })
                                        }
                                    }
                                }

                                DIRECTION.SHORT -> {
                                    val ordersForUpdate = orders.filter { it.value.type == TYPE.MARKET }

                                    ordersForUpdate.forEach { (k, v) ->
                                        if (v.lastBorderPrice == null || v.lastBorderPrice!! > currentPrice) {
                                            v.lastBorderPrice = currentPrice

                                            if (
                                                v.stopPrice?.run { this > currentPrice - botSettings.triggerDistance } == true
                                                || v.stopPrice == null && k.toBigDecimal() > (currentPrice + botSettings.triggerDistance + botSettings.enableStopOrderDistance)
                                            ) {
                                                v.stopPrice = currentPrice - botSettings.triggerDistance
                                            }

                                            orders[k] = v
                                        }
                                        if (v.stopPrice?.run { this <= currentPrice } == true) {
                                            log?.debug("${botSettings.name} Order close: $v")
                                            ordersListForRemove.add(k to v.apply { side = SIDE.BUY })
                                        }
                                    }
                                }
                            }

                            var sellSumAmount = BigDecimal.ZERO
                            var buySumAmount = BigDecimal.ZERO

                            ordersListForRemove.forEach {
                                orders.remove(it.first)

                                when (it.second.side) {
                                    SIDE.BUY -> buySumAmount += it.second.origQty
                                    SIDE.SELL -> sellSumAmount += it.second.origQty
                                    else -> {}
                                }
                            }

                            if (buySumAmount > BigDecimal.ZERO)
                                sentOrder(
                                    amount = buySumAmount,
                                    orderSide = SIDE.BUY,
                                    price = currentPrice,
                                    orderType = TYPE.MARKET,
                                    isCloseOrder = true
                                )

                            if (sellSumAmount > BigDecimal.ZERO)
                                sentOrder(
                                    amount = sellSumAmount,
                                    orderSide = SIDE.SELL,
                                    price = currentPrice,
                                    orderType = TYPE.MARKET,
                                    isCloseOrder = true
                                )

                            ordersListForRemove.clear()
                        }

                        is Order -> {
                            if (msg.pair == botSettings.pair) {
                                if (msg.status == STATUS.FILLED) {
                                    if (msg.type == TYPE.LIMIT) {
                                        val order = msg
                                        val ordersForUpdate = orders.filter { (_, v) -> v.orderId == order.orderId }

                                        ordersForUpdate.forEach { (k, _) ->
                                            orders[k] = order.also {
                                                it.type = TYPE.MARKET
                                                it.lastBorderPrice = BigDecimal.ZERO
                                            }

                                            send("Executed LIMIT order:\n```json\n${json(order)}\n```", true)
                                        }
                                    } else send("Executed MARKET order:\n```json\n${json(msg)}\n```", true)
                                }
                            }
                        }

                        is BotEvent -> {
                            when (msg.type) {
                                BotEvent.Type.GET_PAIR_OPEN_ORDERS -> {
                                    val symbols = msg.message.split("[^a-zA-Z]+".toRegex())
                                        .filter { it.isNotBlank() }

                                    send(
                                        client.getOpenOrders(TradePair(symbols[0], symbols[1]))
                                            .joinToString("\n\n")
                                    )
                                }

                                BotEvent.Type.GET_ALL_OPEN_ORDERS -> {
                                    val pairs = msg.message
                                        .split("\\s+".toRegex())
                                        .filter { it.isNotBlank() }
                                        .map { pair ->
                                            val symbols = pair.split("[^a-zA-Z]+".toRegex())
                                                .filter { it.isNotBlank() }
                                            TradePair(symbols[0], symbols[1])
                                        }

                                    client.getAllOpenOrders(pairs)
                                        .forEach { send("${it.key}\n${it.value.joinToString("\n\n")}") }
                                }

                                BotEvent.Type.SHOW_BALANCES -> {
                                    send(
                                        "#AllBalances " +
                                                client.getBalances()
                                                    ?.toList()
                                                    ?.joinToString(prefix = "\n", separator = "\n") {
                                                        it.first + "\n" + it.second.joinToString(prefix = "\n", separator = "\n")
                                                    }
                                    )
                                }

                                BotEvent.Type.INTERRUPT -> {
                                    socket.interrupt()
                                    return
                                }

                                else -> send("${botSettings.name} Unsupported command: ${msg.type}")
                            }
                        }

                        else -> log?.warn("${botSettings.name} Unsupported message: $msg")
                    }

                    msg = if (isEmulate) client.nextEvent() /* only for test */
                    else queue.poll(waitTime)

                } catch (e: InterruptedException) {
                    log?.error("${botSettings.name} ${e.message}", e)
                    send("#Error_${botSettings.name}: \n${printTrace(e)}")
                    if (stopThread) return
                }
            } while (true)

        } catch (e: Exception) {
            log?.error("${botSettings.name} MAIN ERROR:\n", e)
            send("#Error_${botSettings.name}: \n${printTrace(e)}")
        } finally {
            interruptThis()
        }
    }


    private fun sentOrder(
        price: BigDecimal,
        amount: BigDecimal,
        orderSide: SIDE,
        orderType: TYPE,
        isStaticUpdate: Boolean = false,
        isCloseOrder: Boolean = false
    ): Order {

        var retryCount = retrySentOrderCount

        val orderAmount = if (isCloseOrder) amount
        else {
            if (firstBalanceForOrderAmount) amount
            else amount.div8(price)
        }

        log?.info("${botSettings.name} Sent $orderType order with params: price = $price; amount = $orderAmount; side = $orderSide")

        var order = Order(
            orderId = "",
            pair = botSettings.pair,
            price = price,
            origQty = orderAmount,
            executedQty = BigDecimal(0),
            side = orderSide,
            type = orderType,
            status = STATUS.NEW
        )

        do {
            try {
                order = client.newOrder(order, isStaticUpdate, formatAmount, formatPrice)
                log?.debug("${botSettings.name} Order sent: $order")
                return order
            } catch (e: Exception) {

                if (e.message?.contains("Account has insufficient balance for requested action.") == true) {
                    throw Exception(
                        "${botSettings.pair} Account has insufficient balance for requested action.\n" +
                                "#insufficient_${botSettings.pair}_balance_for: $order\n" +
                                "${botSettings.pair.first} = ${client.getAssetBalance(botSettings.pair.first)}\n" +
                                "${botSettings.pair.second} = ${client.getAssetBalance(botSettings.pair.second)}"
                    )
                }

                retryCount--
                send("#Cannot_send_order_${botSettings.name}: $order\nError:\n${printTrace(e, 50)}")
                log?.error("${botSettings.name} Can't send: $order", e)

                e.printStackTrace()
                log?.debug("${botSettings.name} Orders:\n$orders")
                client = newClient(exchangeEnum, api, sec)
                synchronizeOrders()
            }
            try {
                sleep(5000)
            } catch (e: InterruptedException) {
                log?.error("${botSettings.name} ${e.stackTrace}", e)
                send("#Error_${botSettings.name}: \n${printTrace(e)}")
            }

        } while (isUnknown(order) && retryCount > 0)
        interruptThis("Error: Can't send order.")
        throw Exception("${botSettings.name} Error: Can't send order.")
    }


    private fun synchronizeOrders() {
        when (orders) {
            is ObservableHashMap -> {
                orders.readFromFile()

                var price = minRange
                val openOrders = client.getOpenOrders(botSettings.pair)
                while (price <= maxRange) {
                    val priceIn = price.toPrice()
                    openOrders.find { it.price == priceIn.toBigDecimal() }?.let { openOrder ->
                        orders[priceIn] ?: run {
                            if (botSettings.direction == DIRECTION.LONG && openOrder.side == SIDE.BUY) {
                                val orderSize = if (firstBalanceForOrderAmount) botSettings.orderSize
                                else botSettings.orderSize.div8(price)

                                val qty = orderSize.percent(10.toBigDecimal())

                                if (openOrder.origQty in (openOrder.origQty - qty)..(openOrder.origQty + qty)) {
                                    orders[price.toPrice()] = openOrder
                                    log?.info("${botSettings.name} Synchronized Order:\n$openOrder")
                                }
                            } else if (botSettings.direction == DIRECTION.SHORT && openOrder.side == SIDE.SELL) {
                                val orderSize = if (firstBalanceForOrderAmount) botSettings.orderSize
                                else botSettings.orderSize.div8(price)

                                val qty = orderSize.percent(10.toBigDecimal())

                                if (openOrder.origQty in (openOrder.origQty - qty)..(openOrder.origQty + qty)) {
                                    orders[price.toPrice()] = openOrder
                                    log?.info("${botSettings.name} Synchronized Order:\n$openOrder")
                                }
                            }
                        }
                    }
                    price += botSettings.orderDistance
                }

                orders.forEach { (k, v) ->
                    openOrders
                        .find { (v.orderId == it.orderId || v.type == TYPE.MARKET) && v.price?.run { this in minRange..maxRange } ?: false }
                        ?: run {
                            ordersListForRemove.add(k to v)
                            log?.info("${botSettings.name} File order not found in exchange, file Order removed:\n$v")
                        }
                }

                ordersListForRemove.forEach { orders.remove(it.first) }
                ordersListForRemove.clear()

                if (botSettings.setCloseOrders) {
                    price = minRange
                    while (price <= maxRange) {
                        val priceIn = price.toPrice()

                        orders[priceIn]?.let { order -> log?.trace("${botSettings.name} Order already exist: $order") }
                            ?: run {
                                orders[priceIn] = Order(
                                    orderId = "",
                                    pair = botSettings.pair,
                                    price = priceIn.toBigDecimal(),
                                    origQty = if (firstBalanceForOrderAmount) botSettings.orderSize
                                    else botSettings.orderSize.div8(price),
                                    executedQty = BigDecimal(0),
                                    side = if (botSettings.direction == DIRECTION.LONG) SIDE.SELL else SIDE.BUY,
                                    type = TYPE.MARKET,
                                    status = STATUS.FILLED,
                                    lastBorderPrice = BigDecimal.ZERO
                                )
                            }


                        price += botSettings.orderDistance
                    }
                }
            }

            else -> {}
        }
    }

    private fun getOrder(pair: TradePair, orderId: String): Order? {
        var retryCount = retryGetOrderCount
        do {
            try {
                return client.getOrder(pair, orderId)
            } catch (e: Exception) {
                log?.warn(
                    "$pair ${botSettings.name} getOrder trying ${(retryCount - retryGetOrderCount).absoluteValue}:\n",
                    e
                )
                send(
                    "${botSettings.name} #getOrder_${pair}_trying ${(retryCount - retryGetOrderCount).absoluteValue}\n" +
                            printTrace(e, 0)
                )
                sleep(retryGetOrderInterval.toMillis())
            }
        } while (--retryCount > 0)
        throw Exception("Can't get Order! retry = $retryGetOrderCount; interval = $retryGetOrderInterval")
    }

    private fun isUnknown(order: Order?): Boolean =
        order == null || order.status == STATUS.UNSUPPORTED || order.side == SIDE.UNSUPPORTED

    private fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)

    private fun saveBotSettings(botSettings: BotSettings) {
        val settingsDir = File(path)

        if (settingsDir.isDirectory.not()) Files.createDirectories(Paths.get(path))

        val settingsFile = File(settingsPath)

        reWriteObject(botSettings, settingsFile)
    }

    private fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)
}