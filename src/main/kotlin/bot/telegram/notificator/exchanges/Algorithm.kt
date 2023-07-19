package bot.telegram.notificator.exchanges

import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.clients.stream.Stream
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.absoluteValue


abstract class Algorithm(
    val botSettings: BotSettings,
    val queue: LinkedBlockingDeque<CommonExchangeData>,
    val exchangeEnum: ExchangeEnum,
    val conf: Config,
    val api: String,
    val sec: String,
    var client: Client,
    isLog: Boolean,
    val isEmulate: Boolean,
    val sendMessage: (String, Boolean) -> Unit
) : Thread() {
    val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()
    private val firstBalanceForOrderAmount = botSettings.orderBalanceType == "first"
    val minRange = botSettings.tradingRange.first
    val maxRange = botSettings.tradingRange.second
    val ordersListForRemove: MutableList<Pair<String, Order>> = mutableListOf()

    protected val path: String = "exchangeBots/${botSettings.name}"

    private val log = if (isLog) KotlinLogging.logger {} else null

    val waitTime = conf.getDuration("interval.wait_socket_time")!!
    val formatAmount = "%.${botSettings.countOfDigitsAfterDotForAmount}f"
    val formatPrice = "%.${botSettings.countOfDigitsAfterDotForPrice}f"
    private val retryGetOrderCount = conf.getInt("retry.get_order_count")
    private val retrySentOrderCount: Int = conf.getInt("retry.sent_order_count")
    private val retryGetOrderInterval = conf.getDuration("retry.get_order_interval")

    var stopThread = false
    var currentPrice: BigDecimal = 0.toBigDecimal()
    var prevPrice: BigDecimal = 0.toBigDecimal()

    private val settingsPath = "$path/settings.json"

    val ordersPath = "$path/orders"
    val orders: MutableMap<String, Order> =
        if (isEmulate.not()) ObservableHashMap(
            filePath = ordersPath,
            keyToFileName = { key -> key.replace('.', '_') + ".json" },
            fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
        )
        else mutableMapOf()

    var socket: Stream = client.socket(botSettings.pair, interval, queue)

    fun interruptThis(msg: String? = null) {
        socket.interrupt()
        var msgErr = "#Interrupt_${botSettings.pair} Thread, socket.status = ${socket.state}"
        var logErr = "Thread for ${botSettings.pair} Interrupt, socket.status = ${socket.state}"
        msg?.let { msgErr = "$msgErr\nMessage: $it"; logErr = "$logErr\nMessage: $it"; }
        send(msgErr)
        log?.warn(logErr)
        stopThread = true
    }

    fun sentOrder(
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

    fun synchronizeOrders() {
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

    fun getOrder(pair: TradePair, orderId: String): Order? {
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

    fun isUnknown(order: Order?): Boolean =
        order == null || order.status == STATUS.UNSUPPORTED || order.side == SIDE.UNSUPPORTED

    fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)

    fun saveBotSettings(botSettings: BotSettings) {
        val settingsDir = File(path)

        if (settingsDir.isDirectory.not()) Files.createDirectories(Paths.get(path))

        val settingsFile = File(settingsPath)

        reWriteObject(botSettings, settingsFile)
    }

    fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)

    override fun toString(): String = "status = ${socket.state}, settings = $botSettings"
}