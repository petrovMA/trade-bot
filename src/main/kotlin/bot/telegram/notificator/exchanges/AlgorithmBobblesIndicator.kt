package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.rest_controller.Notification
import bot.telegram.notificator.rest_controller.RatioSetting
import com.typesafe.config.Config
import mu.KotlinLogging
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
                                send("Executed order:\n```json\n${json(msg)}\n```", true)
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
                                        sentOrder(
                                            price = price,
                                            amount = order.amount.toBigDecimal() * ratio.buyRatio,
                                            orderSide = side,
                                            orderType = TYPE.LIMIT
                                        )
                                    } else if (ratio.sellRatio > BigDecimal.ZERO && side == SIDE.SELL) {
                                        sentOrder(
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

    private fun getKlineWithIndicator(): Candlestick {
        val now = System.currentTimeMillis()
        return if (abs(now - candlestickList.last().closeTime) > abs(now - klineConstructor.getCandlestick().closeTime))
            klineConstructor.getCandlestick()
        else
            candlestickList.last()
    }
}