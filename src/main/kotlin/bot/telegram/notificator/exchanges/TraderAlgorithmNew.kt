package bot.telegram.notificator.exchanges

import bot.telegram.notificator.*
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
import kotlin.collections.HashMap
import kotlin.math.absoluteValue


class TraderAlgorithmNew(
    conf: Config,
    val queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    private val botSettings: BotSettings,
    private val exchangeEnum: ExchangeEnum = conf.getEnum(ExchangeEnum::class.java, "exchange"),
    private val api: String = conf.getString("api"),
    private val sec: String = conf.getString("sec"),
    private var client: Client = newClient(exchangeEnum, api, sec),
    private val firstSymbol: String = conf.getString("symbol.first")!!,
    private val secondSymbol: String = conf.getString("symbol.second")!!,
    balanceTrade: BigDecimal = conf.getDouble("balance_trade").toBigDecimal(),
    private val cancelUnknownOrdersInterval: ActionInterval = ActionInterval(conf.getDuration("cancel_unknown_orders_interval")),
    private val retrySentOrderCount: Int = conf.getInt("retry_sent_order_count"),
    isLog: Boolean = true,
    private val isEmulate: Boolean = false,
    val sendMessage: (String) -> Unit
) : Thread() {
    private val minRange = botSettings.tradingRange.first
    private val maxRange = botSettings.tradingRange.second
    private val orders: HashMap<String, Order> = HashMap()
    private val ordersListForRemove: MutableList<String> = mutableListOf()

    private val tradePair = TradePair(botSettings.pair)
    private val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()
    private val path: String = "exchange/${tradePair}"

    private val log = if (isLog) KotlinLogging.logger {} else null

    private val mainBalance = 0.0.toBigDecimal()

    private val waitTime = conf.getDuration("interval.wait_socket_time")
    private val formatCount = conf.getString("format.count")
    private val formatPrice = conf.getString("format.price")
    private val retryGetOrderCount = conf.getInt("retry.get_order_count")
    private val retryGetOrderInterval = conf.getDuration("retry.get_order_interval")

    private val printOrdersAndOff = conf.getBoolean("print_orders_and_off")

    private var stopThread = false
    private var currentPrice: BigDecimal = 0.toBigDecimal()
    private var prevPrice: BigDecimal = 0.toBigDecimal()

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    val balance = BalanceInfo(
        symbols = tradePair,
        firstBalance = 0.0.toBigDecimal(),
        secondBalance = mainBalance,
        balanceTrade = balanceTrade
    )

    private var socket: SocketThread = client.socket(tradePair, interval, queue)

    fun interruptThis(msg: String? = null) {
        socket.interrupt()
        var msgErr = "#Interrupt_$tradePair Thread, socket.status = ${socket.state}"
        var logErr = "Thread for $tradePair Interrupt, socket.status = ${socket.state}"
        msg?.let { msgErr = "$msgErr\nMessage: $it"; logErr = "$logErr\nMessage: $it"; }
        sendMessage(msgErr)
        log?.warn(logErr)
        stopThread = true
    }

    override fun run() {
        stopThread = false
        try {
            if (!File(path).isDirectory)
                Files.createDirectories(Paths.get(path))

            if (printOrdersAndOff) return

            synchronizeOrders()


            socket.run()

            var msg = if (isEmulate) client.nextEvent() /* only for test */
            else queue.poll(waitTime)

            do {
                if (stopThread) return
                try {
                    when (msg) {
                        is Trade -> {
                            prevPrice = currentPrice
                            currentPrice = msg.price
                            log?.debug("$tradePair TradeEvent:\n$msg")

                            from = if (from > msg.time) msg.time else from
                            to = if (to < msg.time) msg.time else to

                            if (currentPrice > minRange && currentPrice < maxRange) {

                                val price = (currentPrice - (currentPrice % botSettings.orderDistance)).toPrice()

                                when (botSettings.ordersType) {
                                    TYPE.MARKET -> {
                                        orders[price]?.let { log?.trace("Order already exist: $it") } ?: run {
                                            if (orders.size < botSettings.orderMaxQuantity) {
                                                orders[price] =
                                                    sentMarketOrder(
                                                        amount = botSettings.orderSize,
                                                        orderSide = if (botSettings.direction == DIRECTION.LONG) SIDE.BUY
                                                        else SIDE.SELL
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
                                                var keyPrice = price.toBigDecimal()
                                                while (keyPrice > minRange) {
                                                    keyPrice.toPrice().let {
                                                        orders[it]?.let { order -> log?.trace("Order already exist: $order") }
                                                            ?: run {
                                                                orders[it] =
                                                                    sentOrder(
                                                                        price = keyPrice,
                                                                        amount = botSettings.orderSize,
                                                                        orderSide = SIDE.BUY
                                                                    )
                                                            }
                                                    }
                                                    keyPrice -= botSettings.orderDistance
                                                }


                                                val prev =
                                                    (prevPrice - (prevPrice % botSettings.orderDistance)).toPrice()

                                                if (price.toBigDecimal() != prev.toBigDecimal()) {
                                                    orders[prev]?.let { order ->
                                                        val o = getOrder(tradePair, order.orderId)

                                                        if (o.status == STATUS.FILLED || o.status == STATUS.REJECTED) {
                                                            orders[prev] = o.also {
                                                                it.type = TYPE.MARKET
                                                                it.lastBorderPrice = BigDecimal.ZERO
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
                                    else -> throw UnsupportedOrderTypeException()
                                }
                            } else
                                log?.warn("Price ${format(currentPrice)}, not in range: ${botSettings.tradingRange}")

                            when (botSettings.direction) {
                                DIRECTION.LONG -> {
                                    orders.filter { it.value.type == TYPE.MARKET }.forEach {
                                        if (it.value.lastBorderPrice!! < currentPrice) {
                                            it.value.lastBorderPrice = currentPrice

                                            if (it.value.stopPrice?.run { this < currentPrice - botSettings.triggerDistance } == true || it.value.stopPrice == null && it.key.toBigDecimal() < (currentPrice - botSettings.triggerDistance)) {
                                                it.value.stopPrice = currentPrice - botSettings.triggerDistance
                                            }
                                        }
                                        if (it.value.stopPrice?.run { this >= currentPrice } == true) {
                                            log?.debug("Order close: ${it.value}")
                                            sentMarketOrder(it.value.origQty, SIDE.SELL)
                                            ordersListForRemove.add(it.key)
                                        }
                                    }
                                }
                                DIRECTION.SHORT -> {
                                    orders.filter { it.value.type == TYPE.MARKET }.forEach {
                                        if (it.value.lastBorderPrice!! > currentPrice) {
                                            it.value.lastBorderPrice = currentPrice

                                            if (it.value.stopPrice?.run { this > currentPrice - botSettings.triggerDistance } == true || it.value.stopPrice == null && it.key.toBigDecimal() < (currentPrice - botSettings.triggerDistance)) {
                                                it.value.stopPrice = currentPrice - botSettings.triggerDistance
                                            }
                                        }
                                        if (it.value.stopPrice?.run { this <= currentPrice } == true) {
                                            log?.debug("Order close: ${it.value}")
                                            sentMarketOrder(it.value.origQty, SIDE.BUY)
                                            orders.remove(it.key)
                                        }
                                    }
                                }
                            }
                            ordersListForRemove.forEach { orders.remove(it) }
                            ordersListForRemove.clear()
                        }
                        is BotEvent -> {
                            when (msg.type) {
                                BotEvent.Type.GET_PAIR_OPEN_ORDERS -> {
                                    val symbols = msg.message.split("[^a-zA-Z]+".toRegex())
                                        .filter { it.isNotBlank() }

                                    sendMessage(
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
                                        .forEach { sendMessage("${it.key}\n${it.value.joinToString("\n\n")}") }
                                }
//                                        SHOW_ALL_BALANCES -> {
//                                            sendMessage(
//                                                    "#AllBalances " +
//                                                            client.getBalances()
//                                                                    .joinToString(prefix = "\n", separator = "\n")
//                                            )
//                                        }
                                BotEvent.Type.SHOW_BALANCES -> {
                                    sendMessage(
                                        "#AllBalances " +
                                                client.getBalances()
                                                    .joinToString(prefix = "\n", separator = "\n")
                                    )
                                }
//                                        SHOW_FREE_BALANCES -> {
//                                            sendMessage(
//                                                    "#FreeBalances " +
//                                                            getFreeBalances(client,
//                                                                    msg.message
//                                                                            .split("\\s+".toRegex())
//                                                                            .let { it.subList(1, it.size) }
//                                                            )
//                                                                    .sortedBy { it.first }
//                                                                    .joinToString(prefix = "\n", separator = "\n") { "${it.first} ${it.second}" }
//                                            )
//                                        }
                                BotEvent.Type.SHOW_GAP -> {
                                    if (balance.orderB != null && balance.orderS != null)
                                        sendMessage(
                                            "#Gap $tradePair\n${
                                                calcGapPercent(
                                                    balance.orderB!!,
                                                    balance.orderS!!
                                                )
                                            }"
                                        )
                                    else
                                        sendMessage("#orderB_or_orderS_is_NULL_Cannot_calc_Gap.")
                                }
                                BotEvent.Type.INTERRUPT -> {
                                    socket.interrupt()
                                    return
                                }
                                else -> sendMessage("Unsupported command: ${msg.type}")
                            }
                        }
                        else -> log?.warn("Unsupported message: $msg")
                    }

                    msg = if (isEmulate) client.nextEvent() /* only for test */
                    else queue.poll(waitTime)

                } catch (e: InterruptedException) {
                    log?.error("$tradePair ${e.message}", e)
                    sendMessage("#Error_$tradePair: \n${printTrace(e)}")
                    if (stopThread) return
                }
            } while (true)

        } catch (e: Exception) {
            log?.error("$tradePair MAIN ERROR:\n", e)
            sendMessage("#Error_$tradePair: \n${printTrace(e)}")
        } finally {
            interruptThis()
        }
    }


    private fun sentOrder(
        price: BigDecimal,
        amount: BigDecimal,
        orderSide: SIDE,
        isStaticUpdate: Boolean = false
    ): Order {

        log?.info("$tradePair Sent order with params: price = $price; count = $amount; side = $orderSide")

        var retryCount = retrySentOrderCount

        var order = Order("", tradePair, price, amount, BigDecimal(0), orderSide, TYPE.LIMIT, STATUS.NEW)

        do {
            try {
                order = client.newOrder(order, isStaticUpdate, formatCount, formatPrice)
                log?.debug("$tradePair Order sent: $order")
                return order
            } catch (e: Exception) {

                if (e.message?.contains("Account has insufficient balance for requested action.") == true) {
                    throw Exception(
                        "$tradePair Account has insufficient balance for requested action.\n" +
                                "#insufficient_${tradePair}_balance_for: $order\n" +
                                "$firstSymbol = ${client.getAssetBalance(firstSymbol).free}\n" +
                                "$secondSymbol = ${client.getAssetBalance(secondSymbol).free}"
                    )
                }

                retryCount--
                sendMessage("#Cannot_send_order_$tradePair: $order\nError:\n${printTrace(e, 50)}")
                log?.error("$tradePair Can't send: $order", e)

                e.printStackTrace()
                log?.debug("$tradePair Balances:\nBuy = ${balance.orderB}\nSell = ${balance.orderS}")
                client = newClient(exchangeEnum, api, sec)
                cancelOrInit()
            }
            try {
                sleep(5000)
            } catch (e: InterruptedException) {
                log?.error("$tradePair ${e.stackTrace}", e)
                sendMessage("#Error_$tradePair: \n${printTrace(e)}")
            }

        } while (isUnknown(order) && retryCount > 0)
        interruptThis("Error: Can't send order.")
        throw Exception("$tradePair Error: Can't send order.")
    }

    private fun sentMarketOrder(amount: BigDecimal, orderSide: SIDE): Order {

        log?.info("$tradePair Sent market order with params: count = $amount; side = $orderSide")

        var retryCount = retrySentOrderCount

        var order = Order("", tradePair, 0.toBigDecimal(), amount, BigDecimal(0), orderSide, TYPE.MARKET, STATUS.NEW)

        do {
            try {
                order = client.newOrder(order, false, formatCount, formatPrice)
                log?.debug("$tradePair Order sent: $order")
                return order
            } catch (e: Exception) {

                if (e.message?.contains("Account has insufficient balance for requested action.") == true) {
                    throw Exception(
                        "$tradePair Account has insufficient balance for requested action.\n" +
                                "#insufficient_${tradePair}_balance_for: $order\n" +
                                "$firstSymbol = ${client.getAssetBalance(firstSymbol).free}\n" +
                                "$secondSymbol = ${client.getAssetBalance(secondSymbol).free}"
                    )
                }

                retryCount--
                sendMessage("#Cannot_send_order_$tradePair: $order\nError:\n${printTrace(e, 50)}")
                log?.error("$tradePair Can't send: $order", e)

                e.printStackTrace()
                log?.debug("$tradePair Balances:\nBuy = ${balance.orderB}\nSell = ${balance.orderS}")
                client = newClient(exchangeEnum, api, sec)
                cancelOrInit()
            }
            try {
                sleep(5000)
            } catch (e: InterruptedException) {
                log?.error("$tradePair ${e.stackTrace}", e)
                sendMessage("#Error_$tradePair: \n${printTrace(e)}")
            }

        } while (isUnknown(order) && retryCount > 0)
        interruptThis("Error: Can't send order.")
        throw Exception("$tradePair Error: Can't send order.")
    }

    private fun cancelOrder(symbols: TradePair, order: Order, isStaticUpdate: Boolean) {
        val tryTimes = 5
        var trying = 0
        do {
            try {
                client.cancelOrder(symbols, order.orderId, isStaticUpdate)
                log?.debug("$symbols Cancelled order: $order")
                break
            } catch (e: Exception) {
                ++trying
                e.printStackTrace()
                log?.warn("$symbols ${e.stackTrace}", e)
                sendMessage("#Warn_$symbols: $\n${printTrace(e)}")

                if (trying > tryTimes) {
                    log?.error("$symbols can't cancel order ${e.stackTrace}", e)
                    sendMessage("#Error_cannot_cancel_order_$symbols:\n${printTrace(e)}")
                    interruptThis()
                    throw e
                } else {
                    sleep(1.m().toMillis())
                    client = newClient(exchangeEnum, api, sec)
                    val status = getOrder(balance.symbols, order.orderId).status
                    if (status != STATUS.NEW && status != STATUS.PARTIALLY_FILLED) {
                        log?.warn("$symbols Order already cancelled: $order")
                        sendMessage("#Order_already_cancelled_$symbols: $order")
                        break
                    } else
                        log?.debug("$symbols Trying $trying to cancel order: $order")
                }
            }
        } while (true)
    }

    private fun cancelOrInit() {
        val orders = client.getOpenOrders(balance.symbols)
        var orderId: String
        for (order in orders) {
            orderId = order.orderId
            when (orderId) {
                balance.orderB!!.orderId -> {
                    balance.orderB = order
                    log?.info("$tradePair Order buy Synchronized:\n$order")
                }
                balance.orderS!!.orderId -> {
                    balance.orderS = order
                    log?.info("$tradePair Order sell Synchronized:\n$order")
                }
                else -> {
                    client.cancelOrder(balance.symbols, orderId, false)
                    log?.info("$tradePair Order cancelled:\n$order")
                }
            }
        }
        log?.info("$tradePair All orders Synchronized")
    }


    private fun synchronizeOrders() {
        if (File("$path/orderB.json").exists()) {
            val oldOrder = readObjectFromFile(File("$path/orderB.json"), Order::class.java)

            balance.orderB = getOrder(balance.symbols, oldOrder.orderId)

            if (balance.orderB?.status == STATUS.FILLED) {
                sendMessage("#$tradePair OrderB sync #FILLED:\n${strOrder(balance.orderB)}")
                if (!isEmulate) writeLine(balance, File("$path/balance.json"))
            }

            log?.info("$tradePair OrderB status: ${balance.orderB}")

            if (balance.orderB!!.status != STATUS.NEW && balance.orderB!!.status != STATUS.PARTIALLY_FILLED) {
                removeFile(File("$path/orderB.json"))
                balance.orderB = null
            } else {
                if (!isEmulate) reWriteObject(balance.orderB!!, File("$path/orderB.json"))

                log?.info("$tradePair orderB synchronized: ${balance.orderB}")
            }
        }

        if (File("$path/orderS.json").exists()) {
            val oldOrder = readObjectFromFile(File("$path/orderS.json"), Order::class.java)

            balance.orderS = getOrder(balance.symbols, oldOrder.orderId)

            if (balance.orderS?.status == STATUS.FILLED) {
                sendMessage("#$tradePair OrderS sync #FILLED:\n${strOrder(balance.orderS)}")
                if (!isEmulate) writeLine(balance, File("$path/balance.json"))
            }

            log?.info("$tradePair orderS status: ${balance.orderS}")

            if (balance.orderS!!.status != STATUS.NEW && balance.orderS!!.status != STATUS.PARTIALLY_FILLED) {
                removeFile(File("$path/orderS.json"))
                balance.orderS = null
            } else {
                if (!isEmulate) reWriteObject(balance.orderS!!, File("$path/orderS.json"))

                log?.info("$tradePair orderS synchronized: ${balance.orderS}")
            }
        }
    }

    private fun getOrder(pair: TradePair, orderId: String): Order {
        var retryCount = retryGetOrderCount
        do {
            try {
                return client.getOrder(pair, orderId)
            } catch (e: Exception) {
                log?.warn("$pair getOrder trying ${(retryCount - retryGetOrderCount).absoluteValue}:\n", e)
                sendMessage(
                    "#getOrder_${pair}_trying ${(retryCount - retryGetOrderCount).absoluteValue}\n" +
                            printTrace(e, 0)
                )
                sleep(retryGetOrderInterval.toMillis())
            }
        } while (--retryCount > 0)
        throw Exception("Can't get Order! retry = $retryGetOrderCount; interval = $retryGetOrderInterval")
    }

    private fun strOrder(order: Order?) =
        if (order == null) "Order is null"
        else "price = ${format(order.price)}" +
                "\nqty = ${order.executedQty}/${order.origQty} | ${order.side} ${order.status}"

    private fun isUnknown(order: Order?): Boolean =
        order == null || order.status == STATUS.UNSUPPORTED || order.side == SIDE.UNSUPPORTED

    private fun cancelUnknownOrders(orders: List<Order>? = null) = cancelUnknownOrdersInterval.tryInvoke {
        (orders ?: client.getOpenOrders(tradePair)).forEach {
            if (it.orderId != balance.orderB?.orderId && it.orderId != balance.orderS?.orderId)
                cancelOrder(tradePair, it, false)
        }
    }

    private fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)
}