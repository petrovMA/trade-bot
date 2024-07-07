package bot.trade.exchanges

import bot.trade.exchanges.*
import bot.trade.exchanges.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.clients.CommonExchangeData
import bot.trade.libs.CustomFileLoggingProcessor
import bot.trade.libs.printTrace
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


class AlgorithmGrid(
    botSettings: BotSettingsGrid,
    exchangeBotsFiles: String,
    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    private val api: String = conf.getString("api"),
    private val sec: String = conf.getString("sec"),
    private var client: Client = newClient(exchangeEnum, api, sec),
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
    private val minRange = botSettings.parameters.tradingRange.lowerBound
    private val maxRange = botSettings.parameters.tradingRange.upperBound
    private val orders: HashMap<String, Order> = HashMap()
    private val ordersListForRemove: MutableList<String> = mutableListOf()

    private val tradePair = botSettings.pair
    private val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()
    private val path: String = "exchange/${botSettings.pair}"

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
        symbols = botSettings.pair,
        firstBalance = 0.0.toBigDecimal(),
        secondBalance = mainBalance,
        balanceTrade = balanceTrade
    )

    override fun handle(msg: CommonExchangeData?) {
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
                        stopThread = true
                        return
                    }

                    else -> sendMessage("Unsupported command: ${msg.type}")
                }
            }

            else -> log?.warn("Unsupported message: $msg")
        }
    }

    private fun getOrder(pair: TradePair, orderId: String): Order {
        var retryCount = retryGetOrderCount
        do {
            try {
                return client.getOrder(pair, orderId) ?:
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

    private fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)
}