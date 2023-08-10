package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.rest_controller.Notification
import com.typesafe.config.Config
import info.bitrich.xchangestream.binancefuture.dto.BinanceFuturesPosition
import mu.KotlinLogging
import org.knowm.xchange.derivative.FuturesContract
import java.io.File
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.collections.HashMap
import kotlin.math.abs


class AlgorithmBobblesIndicator(
    botSettings: BotSettings,
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

    var positions: VirtualPositions = readObject<VirtualPositions>("$path/positions.json") ?: VirtualPositions()
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
                            log?.trace("${settings.pair} TradeEvent:\n$msg")

                            klineConstructor.nextKline(msg).forEach { kline ->
                                if (kline.isClosed) {
                                    candlestickList.add(kline.candlestick)
                                    log?.debug("${settings.pair} Kline closed:\n${kline.candlestick}")
                                }
                            }
                        }

                        is BinanceFuturesPosition -> {
                            if (TradePair(msg.futuresContract) == settings.pair) {
                                exchangePosition = ExchangePosition(msg)
                                log?.info("ExchangePosition:\n$msg")
                            }
                        }
                        // todo ADD Balance listener
                        // todo ADD BinanceFuturesPositions listener

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
                                        // todo ADD FILLED ORDER TO DB

                                        updatePositions(
                                            Notification(
                                                price = msg.price!!,
                                                amount = msg.executedQty - msg.executedQty.percent(settings.feePercent),
                                                pair = settings.pair.toString(),
                                                type = msg.side.toString(),
                                            ),
                                            price = msg.price!!
                                        )
                                    }

                                    STATUS.CANCELED, STATUS.REJECTED -> orders.remove(msg.orderId)
                                    else -> log?.info("${settings.name} Unsupported order status: ${msg.status}")
                                }

                                send(
                                    "#${msg.status} Order update:\n```json\n$msg\n```\n\n" +
                                            "Position:\n```${exchangePosition?.let { json(it) }}```", true
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
                                    // todo ADD SIGNAL TO DB

                                    // find kline with indicator
                                    val kline = getKlineWithIndicator()
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
                                            sentOrder(
                                                price = price,
                                                amount = notification.amount * settings.sellAmountMultiplication,
                                                orderSide = side,
                                                orderType = TYPE.LIMIT
                                            )
                                        }
                                    }

                                    updatePositions(notification, notification.price ?: price)

                                    log?.info("Positions After update:\n$positions")

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

    private fun getKlineWithIndicator(): Candlestick {
        val now = System.currentTimeMillis()
        return if (abs(now - candlestickList.last().closeTime) > abs(now - klineConstructor.getCandlestick().closeTime))
            klineConstructor.getCandlestick()
        else
            candlestickList.last()
    }

    data class VirtualPositions(
        var sellAmount: BigDecimal = BigDecimal.ZERO,
        var sellPrice: BigDecimal = BigDecimal.ZERO,
        var buyAmount: BigDecimal = BigDecimal.ZERO,
        var buyPrice: BigDecimal = BigDecimal.ZERO
    )

    private fun updatePositions(order: Notification, price: BigDecimal) {
        positions = readObject<VirtualPositions>("$path/positions.json") ?: VirtualPositions()

        positions = if (order.type.equals("buy", true)) {
            if (positions.sellPrice < price && positions.sellAmount > order.amount) {
                positions.apply {
                    sellPrice =
                        (positions.sellPrice + (price - positions.sellPrice) * (order.amount / positions.sellAmount)).round()
                    sellAmount = (positions.sellAmount - order.amount).round()
                }
            } else {
                val newOrderAmount = positions.buyAmount + order.amount
                val priceChange = (price - positions.buyPrice) * (order.amount / newOrderAmount)
                positions.apply {
                    buyAmount = newOrderAmount.round()
                    buyPrice = (positions.buyPrice + priceChange).round()
                }
            }
        } else {
            if (positions.buyPrice > price && positions.buyAmount > order.amount) {
                positions.apply {
                    buyPrice =
                        (positions.buyPrice + (price - positions.buyPrice) * (order.amount / positions.buyAmount)).round()
                    buyAmount = (positions.buyAmount - order.amount).round()
                }
            } else {
                val newOrderAmount = positions.sellAmount + order.amount
                val priceChange = (price - positions.sellPrice) * (order.amount / newOrderAmount)
                positions.apply {
                    sellAmount = newOrderAmount.round()
                    sellPrice = (positions.sellPrice + priceChange).round()
                }
            }
        }

        reWriteObject(positions, File("$path/positions.json"))
    }

    data class ExchangePosition(
        val contract: FuturesContract,
        val positionAmount: BigDecimal,
        val entryPrice: BigDecimal,
        val accumulatedRealized: BigDecimal,
        val unrealizedPnl: BigDecimal,
        val marginType: String,
        val isolatedWallet: BigDecimal,
        val positionSide: String?
    ) {
        constructor(position: BinanceFuturesPosition) : this(
            position.futuresContract,
            position.positionAmount.round(),
            position.entryPrice.round(),
            position.accumulatedRealized.round(),
            position.unrealizedPnl.round(),
            position.marginType,
            position.isolatedWallet.round(),
            position.positionSide
        )
    }

    override fun toString(): String = "status = $state, settings = $settings"

    private fun updateSettings(botSettings: BotSettingsBobblesIndicator) {
        settings = botSettings
        saveBotSettings(settings)
    }
}