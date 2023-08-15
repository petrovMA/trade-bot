package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.database.data.entities.NotificationType
import bot.telegram.notificator.database.service.OrderService
import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.rest_controller.Notification
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.collections.HashMap
import kotlin.math.abs


class AlgorithmBobblesIndicator(
    botSettings: BotSettings,
    exchangeBotsFiles: String,
    private val orderService: OrderService?,
    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    api: String = conf.getString("api"),
    sec: String = conf.getString("sec"),
    client: Client = newClient(exchangeEnum, api, sec),
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
    sendMessage = sendMessage
) {
    private var settings: BotSettingsBobblesIndicator = super.botSettings as BotSettingsBobblesIndicator

    private val log = if (isLog) KotlinLogging.logger {} else null

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    private var lastTradePrice: BigDecimal = 0.toBigDecimal()
    private var klineConstructor = KlineConstructor(interval)
    private var candlestickList = ListLimit<Candlestick>(limit = 50)
    override val orders: MutableMap<String, Order> = HashMap()
    private val balances: MutableMap<String, Balance> = HashMap()

    var positions: VirtualPositions = readObject<VirtualPositions>("$path/positions.json")
        ?: VirtualPositions().also {
            var updatedPositions = VirtualPositions()

            orderService
                ?.getAllOrdersByBotNameAndNotificationType(botSettings.name, NotificationType.SIGNAL)
                ?.forEach { order ->
                    updatedPositions = if (order.orderSide == SIDE.BUY)
                        updatePositionsBuy(order.amount!!, order.price!!, updatedPositions)
                    else
                        updatePositionsSell(order.amount!!, order.price!!, updatedPositions)
                }

            orderService
                ?.getAllOrdersByBotNameAndNotificationType(botSettings.name, NotificationType.ORDER_FILLED)
                ?.forEach { order ->
                    updatedPositions = if (order.orderSide == SIDE.BUY)
                        updatePositionsBuy(order.amount!!.negate(), order.price!!, updatedPositions)
                    else
                        updatePositionsSell(order.amount!!.negate(), order.price!!, updatedPositions)
                }

            it.buyAmount = updatedPositions.buyAmount
            it.buyPrice = updatedPositions.buyPrice
            it.sellAmount = updatedPositions.sellAmount
            it.sellPrice = updatedPositions.sellPrice

            reWriteObject(it, File("$path/positions.json"))
        }

    private var exchangePosition: ExchangePosition? = null

    override fun run() {
//        saveBotSettings(botSettings)
        stopThread = false
        try {
//            if (File(ordersPath).isDirectory.not()) Files.createDirectories(Paths.get(ordersPath))

//            synchronizeOrders()

            stream.run { start() }

            var msg = if (isEmulate) client.nextEvent() /* only for test */
            else queue.poll(waitTime)

            do {
                if (stopThread) return
                try {
                    when (msg) {
                        is Trade -> {
                            lastTradePrice = msg.price
                            log?.trace("{} TradeEvent:\n{}", settings.pair, msg)

                            klineConstructor.nextKline(msg).forEach { kline ->
                                if (kline.isClosed) {
                                    candlestickList.add(kline.candlestick)
                                    log?.debug("{} Kline closed:\n {}", settings.pair, kline.candlestick)
                                }
                            }
                        }

                        is ExchangePosition -> exchangePosition = msg

                        is Balance -> balances[msg.asset] = msg

                        is Order -> {
                            if (msg.pair == settings.pair) {

                                log?.info("OrderUpdate:\n$msg")

                                when (msg.status) {
                                    STATUS.NEW -> orders[msg.orderId] = msg
                                    STATUS.PARTIALLY_FILLED -> {
                                        if (orders[msg.orderId] == null)
                                            synchronizeOrders()
                                    }

                                    STATUS.FILLED -> {
                                        orders.remove(msg.orderId)

                                        try {
                                            orderService?.saveOrder(
                                                bot.telegram.notificator.database.data.entities.Order(
                                                    botName = settings.name,
                                                    orderId = msg.orderId,
                                                    tradePair = msg.pair.toString(),
                                                    orderSide = msg.side,
                                                    amount = msg.executedQty - msg.executedQty.percent(settings.feePercent),
                                                    price = msg.price,
                                                    notificationType = NotificationType.ORDER_FILLED,
                                                    dateTime = Timestamp(System.currentTimeMillis())
                                                )
                                            )
                                        } catch (t: Throwable) {
                                            send("Error while saving order to db: ${t.message}")
                                            log?.error("Error while saving order to db: ${t.message}")
                                        }

                                        positions =
                                            readObject<VirtualPositions>("$path/positions.json") ?: VirtualPositions()

                                        positions = if (msg.side == SIDE.BUY)
                                            updatePositionsBuy(
                                                msg.executedQty - msg.executedQty.percent(settings.feePercent),
                                                msg.price!!
                                            )
                                        else
                                            updatePositionsSell(
                                                msg.executedQty - msg.executedQty.percent(settings.feePercent),
                                                msg.price!!
                                            )

                                        reWriteObject(positions, File("$path/positions.json"))

                                    }

                                    STATUS.CANCELED, STATUS.REJECTED -> orders.remove(msg.orderId)
                                    else -> log?.info("${settings.name} Unsupported order status: ${msg.status}")
                                }

                                send(
                                    "#${msg.status} Order update:\n```json\n$msg```\n\n" +
                                            "Position:\n```json\n${exchangePosition?.let { json(it) }}```\n\n" +
                                            "Balances:\n```json\n${json(balances.map { it.value })}```", true
                                )
                            }
                        }

                        is BotEvent -> {
                            when (msg.type) {
                                BotEvent.Type.GET_PAIR_OPEN_ORDERS -> {
                                    val symbols = msg.text.split("[^a-zA-Z]+".toRegex())
                                        .filter { it.isNotBlank() }

                                    send(
                                        client.getOpenOrders(TradePair(symbols[0], symbols[1]))
                                            .joinToString("\n\n")
                                    )
                                }

                                BotEvent.Type.GET_ALL_OPEN_ORDERS -> {
                                    val pairs = msg.text
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
                                                        it.first + "\n" + it.second.joinToString(
                                                            prefix = "\n",
                                                            separator = "\n"
                                                        )
                                                    }
                                    )
                                }

                                BotEvent.Type.CREATE_ORDER -> {
                                    val notification = msg.text.deserialize<Notification>()

                                    try {
                                        orderService?.saveOrder(
                                            bot.telegram.notificator.database.data.entities.Order(
                                                botName = settings.name,
                                                tradePair = settings.pair.toString(),
                                                orderSide = if (notification.type == "buy") SIDE.BUY else SIDE.SELL,
                                                amount = notification.amount,
                                                price = notification.price,
                                                notificationType = NotificationType.SIGNAL,
                                                dateTime = Timestamp(System.currentTimeMillis())
                                            )
                                        )
                                    } catch (t: Throwable) {
                                        send("Error while saving order to db: ${t.message}")
                                        log?.error("Error while saving order to db: ${t.message}")
                                    }

                                    // find kline with indicator
                                    getKlineWithIndicator()?.let { kline ->
                                        log?.info("Kline with indicator:\n$kline")
                                        val side = if (notification.type == "buy") SIDE.BUY else SIDE.SELL
                                        val price = if (side == SIDE.BUY) kline.low else kline.high

                                        if (notification.amount > settings.minOrderSize
                                            && notification.price == null
                                            && notification.placeOrder
                                        ) {

                                            if (settings.buyAmountMultiplication > BigDecimal.ZERO && side == SIDE.BUY) {
                                                sentOrder(
                                                    price = price,
                                                    amount = notification.amount * settings.buyAmountMultiplication,
                                                    orderSide = side,
                                                    orderType = TYPE.LIMIT
                                                )
                                            } else if (settings.sellAmountMultiplication > BigDecimal.ZERO && side == SIDE.SELL) {

                                                val shortPositionAndShortOrders = ((exchangePosition?.positionAmount
                                                    ?: 0.toBigDecimal()) - shortOrdersSum()).abs()

                                                if (settings.maxShortPosition > shortPositionAndShortOrders)
                                                    sentOrder(
                                                        price = price,
                                                        amount = notification.amount * settings.sellAmountMultiplication,
                                                        orderSide = side,
                                                        orderType = TYPE.LIMIT
                                                    )
                                                else send(
                                                    "Short position and sum of short orders are too big: " +
                                                            "$shortPositionAndShortOrders, limit is: ${settings.maxShortPosition}"
                                                )
                                            }
                                        }

                                        positions =
                                            readObject<VirtualPositions>("$path/positions.json") ?: VirtualPositions()

                                        positions = if (notification.type.equals("buy", true))
                                            updatePositionsBuy(notification.amount, notification.price ?: price)
                                        else
                                            updatePositionsSell(notification.amount, notification.price ?: price)

                                        reWriteObject(positions, File("$path/positions.json"))

                                        log?.info("Positions After update:\n$positions")
                                    } ?: send("Can't generate Kline, not enough trades")

                                }

                                BotEvent.Type.SET_SETTINGS -> {
                                    updateSettings(msg.text.deserialize())

                                    send("Settings:\n```json\n${json(settings)}```", true)
                                }

                                BotEvent.Type.INTERRUPT -> {
                                    stream.interrupt()
                                    return
                                }

                                else -> send("${settings.name} Unsupported command: ${msg.type}")
                            }
                        }

                        else -> log?.warn("${settings.name} Unsupported message: $msg")
                    }

                    msg = if (isEmulate) client.nextEvent() /* only for test */
                    else queue.poll(waitTime)

                } catch (e: InterruptedException) {
                    log?.error("${settings.name} ${e.message}", e)
                    send("#Error_${settings.name}: \n${printTrace(e)}")
                    if (stopThread) return
                }
            } while (true)

        } catch (e: Exception) {
            log?.error("${settings.name} MAIN ERROR:\n", e)
            send("#Error_${settings.name}: \n${printTrace(e)}")
        } finally {
            interruptThis()
        }
    }

    override fun synchronizeOrders() {
        orders.clear()

        client
            .getAllOpenOrders(listOf(settings.pair))[settings.pair]
            ?.forEach { orders[it.orderId] = it }
    }

    private fun getKlineWithIndicator(): Candlestick? {
        return if (candlestickList.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (abs(now - candlestickList.last().closeTime) > abs(now - klineConstructor.getCandlestick().closeTime))
                klineConstructor.getCandlestick()
            else
                candlestickList.last()
        } else null
    }

    data class VirtualPositions(
        var sellAmount: BigDecimal = BigDecimal.ZERO,
        var sellPrice: BigDecimal = BigDecimal.ZERO,
        var buyAmount: BigDecimal = BigDecimal.ZERO,
        var buyPrice: BigDecimal = BigDecimal.ZERO
    )

    private fun updatePositionsBuy(
        amount: BigDecimal,
        price: BigDecimal,
        positions: VirtualPositions = this.positions
    ): VirtualPositions {
        return if (positions.sellPrice < price && positions.sellAmount > amount) {
            positions.apply {
                sellPrice =
                    (positions.sellPrice + (price - positions.sellPrice) * (amount / positions.sellAmount)).round()
                sellAmount = (positions.sellAmount - amount).round()
            }
        } else {
            val newOrderAmount = positions.buyAmount + amount
            if (newOrderAmount > BigDecimal(0.0)) {
                val priceChange = (price - positions.buyPrice) * (amount / newOrderAmount)
                positions.apply {
                    buyAmount = newOrderAmount.round()
                    buyPrice = (positions.buyPrice + priceChange).round()
                }
            } else {
                positions.apply {
                    buyAmount = BigDecimal.ZERO
                    buyPrice = BigDecimal.ZERO
                }
            }
        }
    }

    private fun updatePositionsSell(
        amount: BigDecimal,
        price: BigDecimal,
        positions: VirtualPositions = this.positions
    ): VirtualPositions {
        return if (positions.buyPrice > price && positions.buyAmount > amount) {
            positions.apply {
                buyPrice =
                    (positions.buyPrice + (price - positions.buyPrice) * (amount / positions.buyAmount)).round()
                buyAmount = (positions.buyAmount - amount).round()
            }
        } else {
            val newOrderAmount = positions.sellAmount + amount
            if (newOrderAmount > BigDecimal(0.0)) {
                val priceChange = (price - positions.sellPrice) * (amount / newOrderAmount)
                positions.apply {
                    sellAmount = newOrderAmount.round()
                    sellPrice = (positions.sellPrice + priceChange).round()
                }
            } else {
                positions.apply {
                    sellAmount = BigDecimal.ZERO
                    sellPrice = BigDecimal.ZERO
                }
            }
        }
    }

    override fun toString(): String = "status = $state, settings = $settings"

    private fun updateSettings(botSettings: BotSettingsBobblesIndicator) {
        settings = botSettings
        saveBotSettings(settings)
    }

    private fun shortOrdersSum(): BigDecimal = orders
        .map { it.value }
        .filter { it.side == SIDE.SELL }
        .sumOf { it.origQty }
}