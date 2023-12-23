package bot.trade.exchanges

import bot.trade.database.data.entities.NotificationType
import bot.trade.database.service.OrderService
import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.rest_controller.Notification
import com.typesafe.config.Config
import mu.KotlinLogging
import org.knowm.xchange.exceptions.ExchangeException
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
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

    //    private var klineConstructor = KlineConstructor(interval)
    private lateinit var currentKline: Candlestick
    private lateinit var prevKline: Candlestick

    private val orders: MutableMap<String, Order> = if (isEmulate.not()) ObservableHashMap(
        filePath = "$path/orders".also {
            if (isEmulate.not() && File(it).isDirectory.not()) Files.createDirectories(Paths.get(it))
        },
        keyToFileName = { key -> key.replace('.', '_') + ".json" },
        fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
    )
    else mutableMapOf()

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

    override fun setup() {
        client.getCandlestickBars(settings.pair, INTERVAL.FIVE_MINUTES, 5).run {
            currentKline = last()
            prevKline = get(size - 2)
        }
    }

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Candlestick -> {
                log?.trace("{} Kline:\n{}", settings.pair, msg)

                if (msg.closeTime > currentKline.closeTime)
                    prevKline = currentKline

                currentKline = msg
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
                                    bot.trade.database.data.entities.Order(
                                        botName = settings.name,
                                        orderId = msg.orderId,
                                        tradePair = msg.pair.toString(),
                                        orderSide = msg.side,
                                        amount = msg.executedQty,
                                        price = calcPriceWithFee(msg.executedQty, msg.price!!, msg.side),
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
                                    msg.executedQty.negate(),
                                    calcPriceWithFee(msg.executedQty, msg.price!!, msg.side)
                                )
                            else
                                updatePositionsSell(
                                    msg.executedQty.negate(),
                                    calcPriceWithFee(msg.executedQty, msg.price!!, msg.side)
                                )

                            reWriteObject(positions, File("$path/positions.json"))

                        }

                        STATUS.CANCELED, STATUS.REJECTED -> orders.remove(msg.orderId)
                        else -> log?.info("${settings.name} Unsupported order status: ${msg.status}")
                    }

                    send(
                        "#${msg.status} Order update:\n```json\n$msg```\n\n" +
                                "Exchange Position:\n```json\n${exchangePosition?.let { json(it) }}```\n\n" +
                                "Position:\n```json\n${json(positions)}```\n\n" +
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

                        val kline = if (
                            abs(currentKline.closeTime - System.currentTimeMillis())
                            < abs(prevKline.closeTime - System.currentTimeMillis())
                        )
                            currentKline
                        else
                            prevKline

                        log?.info("Kline with indicator:\n$kline")
                        val side = if (notification.type == "buy") SIDE.BUY else SIDE.SELL
                        val price = if (side == SIDE.BUY) kline.low else kline.high

                        try {
                            orderService?.saveOrder(
                                bot.trade.database.data.entities.Order(
                                    botName = settings.name,
                                    tradePair = settings.pair.toString(),
                                    orderSide = if (notification.type == "buy") SIDE.BUY else SIDE.SELL,
                                    amount = notification.amount,
                                    price = price,
                                    notificationType = NotificationType.SIGNAL,
                                    dateTime = Timestamp(System.currentTimeMillis())
                                )
                            )
                        } catch (t: Throwable) {
                            send("Error while saving order to db: ${t.message}")
                            log?.error("Error while saving order to db: ${t.message}")
                        }

                        positions =
                            readObject<VirtualPositions>("$path/positions.json") ?: VirtualPositions()

                        positions = if (notification.type.equals("buy", true))
                            updatePositionsBuy(notification.amount, notification.price ?: price)
                        else
                            updatePositionsSell(notification.amount, notification.price ?: price)

                        reWriteObject(positions, File("$path/positions.json"))

                        log?.info("Positions After update:\n$positions")

                        if (notification.amount > settings.minOrderSize
                            && notification.price == null
                            && notification.placeOrder
                        ) {

                            when (side) {
                                SIDE.BUY -> {
                                    if (settings.buyAmountMultiplication > BigDecimal.ZERO) {
                                        try {
                                            sentOrder(
                                                price = price,
                                                amount = notification.amount * settings.buyAmountMultiplication,
                                                orderSide = side,
                                                orderType = TYPE.LIMIT
                                            )
                                        } catch (e: ExchangeException) {
                                            send("HttpStatusException:\n```\n${e.message}```", true)
                                            log?.warn(e.stackTraceToString())
                                        }
                                    } else send(
                                        "Buy orders disabled, because of buyAmountMultiplication = ${settings.buyAmountMultiplication}" +
                                                "\n\nPosition:\n```json\n${json(positions)}```"
                                    )
                                }

                                SIDE.SELL -> {
                                    if (settings.sellAmountMultiplication > BigDecimal.ZERO) {

                                        val shortPositionAndShortOrders = (exchangePosition?.positionAmount
                                            ?: 0.toBigDecimal()) - shortOrdersSum()

                                        if (settings.maxShortPosition.negate() <= shortPositionAndShortOrders)
                                            try {
                                                sentOrder(
                                                    price = price,
                                                    amount = notification.amount * settings.sellAmountMultiplication,
                                                    orderSide = side,
                                                    orderType = TYPE.LIMIT
                                                )
                                            } catch (e: ExchangeException) {
                                                send("HttpStatusException:\n```\n${e.message}```", true)
                                                log?.warn(e.stackTraceToString())
                                            }
                                        else send(
                                            "Short position and sum of short orders are too big: " +
                                                    "$shortPositionAndShortOrders, limit is: ${settings.maxShortPosition}"
                                        )
                                    } else send(
                                        "Sell orders disabled, because of sellAmountMultiplication = ${settings.sellAmountMultiplication}" +
                                                "\n\nPosition:\n```json\n${json(positions)}```"
                                    )
                                }

                                else -> send("Unsupported OrderSide: $side")
                            }
                        }
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
    }

    override fun synchronizeOrders() {
        orders.clear()

        client
            .getAllOpenOrders(listOf(settings.pair))[settings.pair]
            ?.forEach { orders[it.orderId] = it }
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
            val newOrderAmount = positions.sellAmount - amount
            positions.apply {
                sellPrice = (sellPrice + (price - sellPrice) * (amount / newOrderAmount)).round()
                sellAmount = newOrderAmount.round()
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
            val newOrderAmount = positions.buyAmount - amount
            positions.apply {
                buyPrice = (buyPrice + (price - buyPrice) * (amount / newOrderAmount)).round()
                buyAmount = newOrderAmount.round()
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

    private fun calcPriceWithFee(
        amount: BigDecimal,
        price: BigDecimal,
        side: SIDE,
        feePercent: BigDecimal = settings.feePercent
    ): BigDecimal = when (side) {
        SIDE.BUY -> (amount * price).let { (it + it.percent(feePercent)) / amount }
        SIDE.SELL -> (amount * price).let { (it - it.percent(feePercent)) / amount }
        SIDE.UNSUPPORTED -> price
    }

    override fun calcAmount(amount: BigDecimal, price: BigDecimal) =
        if (firstBalanceForOrderAmount) amount
        else amount.div8(price)
}