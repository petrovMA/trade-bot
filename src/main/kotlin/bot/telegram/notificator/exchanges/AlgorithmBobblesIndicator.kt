package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.rest_controller.Notification
import bot.telegram.notificator.rest_controller.RatioSetting
import com.typesafe.config.Config
import mu.KotlinLogging
import org.knowm.xchange.exceptions.ExchangeException
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
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

    private val log = if (isLog) KotlinLogging.logger {} else null

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    private var lastTradePrice: BigDecimal = 0.toBigDecimal()
    private var klineConstructor = KlineConstructor(interval)
    private var candlestickList = ListLimit<Candlestick>(limit = 50)

    private var ratio = RatioSetting()

    override fun run() {
//        saveBotSettings(botSettings)
        stopThread = false
        try {
//            if (File(ordersPath).isDirectory.not()) Files.createDirectories(Paths.get(ordersPath))

//            synchronizeOrders()

            socket.run { start() }

            var msg = if (isEmulate) client.nextEvent() /* only for test */
            else queue.poll(waitTime)

            do {
                if (stopThread) return
                try {
                    when (msg) {
                        is Trade -> {
                            lastTradePrice = msg.price
                            log?.trace("${botSettings.pair} TradeEvent:\n$msg")

                            klineConstructor.nextKline(msg).forEach { kline ->
                                if (kline.isClosed) {
                                    candlestickList.add(kline.candlestick)
                                    log?.debug("${botSettings.pair} Kline closed:\n${kline.candlestick}")
                                }
                            }
                        }

                        is Order -> {
                            if (msg.pair == botSettings.pair) {
                                send("Order update:\n```json\n${json(msg)}\n```", true)
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
                                    val order = msg.text.deserialize<Notification>()

                                    // find kline with indicator
                                    val kline = getKlineWithIndicator()
                                    log?.info("Kline with indicator:\n$kline")
                                    val side = if (order.type == "buy") SIDE.BUY else SIDE.SELL
                                    val price = if (side == SIDE.BUY) kline.low else kline.high

                                    if (ratio.buyRatio > BigDecimal.ZERO && side == SIDE.BUY) {
                                        sentOrderTEMPLATE(
                                            price = price,
                                            amount = order.amount.toBigDecimal() * ratio.buyRatio,
                                            orderSide = side,
                                            orderType = TYPE.LIMIT
                                        )
                                    } else if (ratio.sellRatio > BigDecimal.ZERO && side == SIDE.SELL) {
                                        sentOrderTEMPLATE(
                                            price = price,
                                            amount = order.amount.toBigDecimal() * ratio.sellRatio,
                                            orderSide = side,
                                            orderType = TYPE.LIMIT
                                        )
                                    }
                                }

                                BotEvent.Type.SET_SETTINGS -> {
                                    ratio = msg.text.deserialize()

                                    send("Settings:\n```json\n${json(ratio)}```", true)
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

    fun sentOrderTEMPLATE(
        price: BigDecimal,
        amount: BigDecimal,
        orderSide: SIDE,
        orderType: TYPE,
        isStaticUpdate: Boolean = false,
        isCloseOrder: Boolean = false
    ): Order? {

        log?.info("${botSettings.name} Sent $orderType order with params: price = $price; amount = $amount; side = $orderSide")

        var order = Order(
            orderId = "",
            pair = botSettings.pair,
            price = price,
            origQty = amount,
            executedQty = BigDecimal(0),
            side = orderSide,
            type = orderType,
            status = STATUS.NEW
        )

            try {
                order = client.newOrder(order, isStaticUpdate, formatAmount, formatPrice)
                log?.debug("${botSettings.name} Order sent: $order")
                return order
            } catch (e: ExchangeException) {
                log?.info("${botSettings.name} ${e.message}")
                return null
            } catch (t: Throwable) {
                log?.error("${botSettings.name} ${t.message}", t)
                send("#Error_${botSettings.name}: \n${printTrace(t)}")
                throw t
            }
    }

    private fun getKlineWithIndicator(): Candlestick {
        val now = System.currentTimeMillis()
        return if (abs(now - candlestickList.last().closeTime) > abs(now - klineConstructor.getCandlestick().closeTime))
            klineConstructor.getCandlestick()
        else
            candlestickList.last()
    }
}