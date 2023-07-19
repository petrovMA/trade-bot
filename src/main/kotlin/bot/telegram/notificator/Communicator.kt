package bot.telegram.notificator

import bot.telegram.notificator.exchanges.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.emulate.Emulate
import bot.telegram.notificator.libs.*
import bot.telegram.notificator.rest_controller.Notification
import bot.telegram.notificator.rest_controller.RatioSetting
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.util.*
import java.util.concurrent.BlockingQueue

class Communicator(
    private val exchangeFiles: File,
    intervalCandlestick: Duration?,
    intervalStatistic: Duration?,
    timeDifference: Duration?,
    candlestickDataCommandStr: String?,
    private val candlestickDataPath: Map<ExchangeEnum, String>,
    val taskQueue: BlockingQueue<Thread>,
    private val cmd: Commands = Commands(),
    private val defaultCommands: Map<Regex, String> = mapOf(
        cmd.commandAllBalance to "commandAllBalance BTC ETH BNB",
        cmd.commandBalance to "commandFreeBalance BTC ETH BNB"
    ),
    private val sendFile: (File) -> Unit,
    val sendMessage: (String, Boolean) -> Unit
) {
    private val log = KotlinLogging.logger {}

    private val intervalCandlestickUpdate: Duration = intervalCandlestick ?: 2.d()
    private val intervalStatistic: Duration = intervalStatistic ?: 4.h()
    private val timeDifference: Duration = timeDifference ?: 0.ms()
    private var symbols = File("pairsSet").useLines { it.toList() }

    private var propertyPairs: Map<String, String> = scanAll(exchangeFiles, symbols)
    private var tradePairs: MutableMap<String, Algorithm> = emptyMap<String, Algorithm>().toMutableMap()

    var candlestickDataCommand: Command = candlestickDataCommandStr?.let {
        try {
            Command.valueOf(it)
        } catch (t: Throwable) {
            Command.NONE
        }
    } ?: Command.NONE

    private val startData: LocalDate? = null

    init {
        log.info("Bot starts!")
        repeatEvery({
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.BINANCE, sendMessage))
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.BITMAX, sendMessage))
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.HUOBI, sendMessage))
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.GATE, sendMessage))
        }, this.intervalCandlestickUpdate, this.timeDifference)
//        repeatEvery({ getStatistics() }, this.intervalStatistic, this.timeDifference)

        // todo ETH_USDT just for test:
        tradePairs["test_ETH_USDT"] = AlgorithmBobblesIndicator(
            botSettings = BotSettings(
                name = "test_ETH_USDT",
                pair = TradePair("ETH_USDT"),
                exchange = "BINANCE",
                direction = DIRECTION.SHORT,
                ordersType = TYPE.LIMIT,
                tradingRange = 0.0.toBigDecimal() to 0.0.toBigDecimal(),
                orderSize = 0.toBigDecimal(), // Order Quantity:: order size
                orderBalanceType = "first",
                orderDistance = 0.toBigDecimal(),
                triggerDistance = 0.toBigDecimal(),
                orderMaxQuantity = 0,
                countOfDigitsAfterDotForAmount = 4,
                countOfDigitsAfterDotForPrice = 2
            ),
            sendMessage = sendMessage,
        )

        tradePairs["test_ETH_USDT"]?.start()
    }

    fun onUpdate(message: String) {
        var msg = ""

        when {
            cmd.commandScan.matches(message) -> {
                symbols = File("pairsSet").useLines { it.toList() }
                this.propertyPairs = scanAll(this.exchangeFiles, symbols)
                    .mapValues {
                        msg += "${it.key}\n"
                        it.value
                    }
                msg = "scanned:\n$msg"
                log.info(msg)
            }

            cmd.commandHelp.matches(message) -> {
                val helpFor = message.split("\\s+".toRegex())[1]
                when (helpFor) {
                    "command1" -> {
                        TODO()
                    }

                    "command2" -> {
                        TODO()
                    }

                    "command3" -> {
                        TODO()
                    }

                    else -> {
                        msg = "Not exist command: $helpFor"
                    }
                }
                log.info(msg)
            }

            cmd.commandEmulateTradeBot.matches(message) -> {

                val params = message.split("[\\n|]".toRegex())
                val (msgErrors, tradeBotSettings) = parseTradeBotSettings(params)

                val (startDate, msg0) = getBotStartParam(params, "startDate", msgErrors)
                val (endDate, msg1) = getBotStartParam(params, "endDate", msg0)
                val (exchange, msg2) = getBotStartParam(params, "exchange", msg1)

                val (firstBalanceStr, msg3) = getBotStartParam(params, "firstBalance", msg2)
                val (secondBalanceStr, msg4) = getBotStartParam(params, "secondBalance", msg3)

                val firstBalance: BigDecimal? = try {
                    firstBalanceStr.toBigDecimal()
                } catch (t: Throwable) {
                    msg += "Incorrect value 'firstBalance': $firstBalanceStr"
                    log.warn("Incorrect value 'firstBalance': $firstBalanceStr", t)
                    null
                }

                val secondBalance: BigDecimal? = try {
                    secondBalanceStr.toBigDecimal()
                } catch (t: Throwable) {
                    msg += "Incorrect value 'secondBalance': $secondBalanceStr"
                    log.warn("Incorrect value 'secondBalance': $secondBalanceStr", t)
                    null
                }

                if (tradeBotSettings != null
                    && firstBalance != null
                    && secondBalance != null
                ) {
                    taskQueue.put(
                        Emulate(
                            sendFile = sendFile,
                            sendMessage = sendMessage,
                            botSettings = tradeBotSettings,
                            firstBalance = firstBalance,
                            secondBalance = secondBalance,
                            startDate = startDate,
                            endDate = endDate,
                            candlestickDataPath = candlestickDataPath,
                            exchangeEnum = ExchangeEnum.valueOf(exchange.uppercase(Locale.getDefault()))
                        )
                    )
                } else msg += "Parse params errors:\n$msg4"

                log.info(msg)
            }

            cmd.commandCreateTradeBot.matches(message) -> {

                val params = message.split("[\\n|]".toRegex())
                val (msgErrors, tradeBotSettings) = parseTradeBotSettings(params)

                tradeBotSettings?.let {
                    tradePairs[it.name] = AlgorithmTrader(tradeBotSettings, sendMessage = sendMessage)
                } ?: run { msg += "Parse params errors:\n$msgErrors" }

                log.info(msg)
            }

            cmd.commandLoadTradeBot.matches(message) -> {

                val params = message.split("\\s+".toRegex())
                val tradeBotSettings =
                    readObjectFromFile(File("exchangeBots/${params[1]}/settings.json"), BotSettings::class.java)

                tradePairs[tradeBotSettings.name] =
                    AlgorithmTrader(tradeBotSettings, sendMessage = sendMessage)

                msg += "Trade bot ${tradeBotSettings.name} loaded, settings:\n```json\n${json(tradeBotSettings)}\n```"
                send(msg, true)
                log.info(msg)

                msg = ""
            }

            cmd.commandStartTradeBot.matches(message) -> {
                val param = message.split("\\s+".toRegex())
                if (param.size == 2) {
                    val key = param[1]
                    msg = tradePairs[key]?.start()?.let {
                        log.info("$key started")
                        "$key started"
                    } ?: run {
                        log.info("$key not exist")
                        "$key not exist"
                    }
                } else {
                    msg = "command 'start' must have one param"
                    log.info("command 'start' must have one param. Msg = $message")
                }
            }

            cmd.commandShowProp.matches(message) -> {
                propertyPairs[message.uppercase(Locale.getDefault()).split("\\s+".toRegex()).last()]?.let {
                    msg = "properties:"
                    readConf(it)
                } ?: let {
                    msg = "command must have to match RegExp '${cmd.commandShowProp}'"
                    log.info("command must have to match RegExp '${cmd.commandShowProp}'")
                }
            }

            cmd.commandCandlestickData.matches(message) -> {
                val param = message.split("\\s+".toRegex())
                if (param.size == 2) {
                    val cmnd = param[1].uppercase(Locale.getDefault())
                    candlestickDataCommand = Command.valueOf(cmnd)
                    msg += "Set CollectCandlestickData command to: $cmnd"
                } else {
                    msg = "command 'candlestick' must have one param"
                    log.info("command 'candlestick' must have one param. Msg = $message")
                }
            }

            cmd.commandDeleteOldCandlestickData.matches(message) -> {
                val params = message.split("\\s+".toRegex())
                taskQueue.put(
                    DeleteOldCandlestickData(
                        ExchangeEnum.valueOf(params[1].uppercase()),
                        sendMessage,
                        params[2].uppercase()
                    )
                )
                msg = "DeleteOldData command accepted!"
            }

            cmd.commandCollect.matches(message) -> {
                val params = message.split("\\s+".toRegex())
                taskQueue.put(
                    CollectCandlestickData(
                        Command.valueOf(params[2].uppercase()),
                        startData,
                        ExchangeEnum.valueOf(params[1].uppercase()),
                        sendMessage
                    )
                )
                msg = "Collect Data command accepted!"
            }

            cmd.commandTradePairsInit.matches(message) -> {
                msg += "Command '${cmd.commandTradePairsInit}' not supported yet!"
            }

            else -> tradePairs.apply {

                when {
                    cmd.commandStatus.matches(message) -> {
                        msg = "${cmd.commandStatus}:\n"
                        forEach { msg += "${it.value.state} => ${it.key}\n" }
                        log.info(msg)
                    }

                    cmd.commandCreateAll.matches(message) -> {
                        msg += "Command '${cmd.commandCreateAll}' not supported yet!"
                    }

                    cmd.commandCreate.matches(message) -> {
                        msg += "Command '${cmd.commandCreate}' not supported yet!"
                    }

                    cmd.commandStartAll.matches(message) -> {
                        filter { it.value.state == Thread.State.NEW }
                            .forEach {
                                it.value.start()
                                msg += "${it.key}\n"
                            }
                        msg = "started pairs:\n$msg"
                        log.info(msg)
                    }

                    cmd.commandStart.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1].uppercase()
                            msg = get(key)?.start()?.let {
                                log.info("$key started")
                                "$key started"
                            } ?: run {
                                log.info("$key not exist")
                                "$key not exist"
                            }
                        } else {
                            msg = "command 'start' must have one param"
                            log.info("command 'start' must have one param. Msg = $message")
                        }
                    }

                    cmd.commandCalcGap.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1].uppercase()
                            msg = get(key)?.run {
                                this.queue.add(BotEvent(message, BotEvent.Type.SHOW_GAP))
                                return
                            } ?: run {
                                log.info("$key not exist")
                                "$key not exist"
                            }
                        } else {
                            msg = "command 'start' must have one param"
                            log.info("command 'start' must have one param. Msg = $message")
                        }
                    }

                    cmd.commandBalance.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        when {
                            param.size > 1 -> values.find { it.isAlive }
                                ?.queue?.add(BotEvent(message, BotEvent.Type.SHOW_BALANCES))
                                ?: run {
                                    log.warn("active 'tradePair' not found for $message")
                                    msg = "active 'tradePair' not found for $message"
                                }

                            param.size == 1 -> defaultCommands[cmd.commandBalance]
                                ?.run {
                                    values.find { it.isAlive }
                                        ?.queue?.add(BotEvent(this, BotEvent.Type.SHOW_BALANCES))
                                        ?: run {
                                            log.warn("active 'tradePair' not found for $message")
                                            msg = "active 'tradePair' not found for $message"
                                        }
                                }
                                ?: run {
                                    log.warn("default Command not found for $message")
                                    msg = "default Command not found for $message"
                                }

                            else -> {
                                msg =
                                    "command 'FreeBalance' must have at least 2 param. Example: 'FreeBalance AIONETH AION WAN'"
                                log.info("command 'start' must have at least 2 param. Msg = $message")
                            }
                        }
                    }

                    cmd.commandAllBalance.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        when {
                            param.size > 1 -> {
                                values.find { it.isAlive }
                                    ?.queue?.add(BotEvent(message, BotEvent.Type.SHOW_ALL_BALANCES))
                                    ?: run {
                                        log.warn("active 'tradePair' not found for $message")
                                        msg = "active 'tradePair' not found for $message"
                                    }
                            }

                            param.size == 1 -> defaultCommands[cmd.commandAllBalance]
                                ?.run {
                                    values.find { it.isAlive }
                                        ?.queue?.add(BotEvent(this, BotEvent.Type.SHOW_ALL_BALANCES))
                                }
                                ?: let {
                                    log.warn("default Command not found for $message")
                                    msg = "default Command not found for $message"
                                }

                            else -> {
                                msg =
                                    "command 'FreeBalance' must have at least 2 param. Example: 'FreeBalance AIONETH AION WAN'"
                                log.info("command 'start' must have at least 2 param. Msg = $message")
                            }
                        }
                    }

                    cmd.commandStopAll.matches(message) -> {
                        filter { it.value.isAlive }
                            .forEach {
                                it.value.interruptThis()
                                msg += it.key
                            }
                        msg = "stopped pairs:\n$msg"
                        log.info(msg)
                    }

                    cmd.commandAllOrders.matches(message) -> {
                        val pairs = map { it.key }.joinToString(",")

                        map { it.value }
                            .find { it.isAlive }
                            ?.apply {
                                if (!queue.add(BotEvent(pairs, BotEvent.Type.GET_ALL_OPEN_ORDERS))) {
                                    log.warn("Command not added to queue $this")
                                    msg = "Command not added to queue $this"
                                }
                            } ?: run { msg = "Working pairs not found!!!" }
                        log.info(msg)
                    }

                    cmd.commandStop.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1]
                            msg = get(key)?.let { value ->
                                value.queue.add(BotEvent(type = BotEvent.Type.INTERRUPT))
                                log.info("$key stopped")
                                "$key stopped"
                            } ?: run {
                                log.info("$key not exist")
                                "$key not exist"
                            }
                        } else {
                            msg = "command 'stop' must have one param"
                            log.info("command 'stop' must have one param. Msg = $message")
                        }
                    }

                    cmd.commandDelete.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1]
                            msg = get(key)?.let { value ->
                                value.queue.add(BotEvent(type = BotEvent.Type.INTERRUPT))
                                log.info("$key stopped")
                                remove(key)
                                log.info("$key deleted")
                                "$key stopped and deleted"
                            } ?: run {
                                log.info("$key not exist")
                                "$key not exist"
                            }
                        } else {
                            msg = "command 'deleted' must have one param"
                            log.info("command 'deleted' must have one param. Msg = $message")
                        }
                    }

                    cmd.commandOrders.matches(message) -> {
                        val param = message.uppercase().split("\\s+".toRegex())
                        if (param.size == 3) {
                            val key = param[1].uppercase()
                            val trade = get(key) ?: values.first()
                            trade.queue.add(BotEvent(param[2], BotEvent.Type.GET_PAIR_OPEN_ORDERS))
                        } else {
                            msg =
                                "command '$cmd.commandOrders' must have only 2 params. Example: '$cmd.commandOrders AIONETH AIONETH'"
                            log.info("command '$cmd.commandOrders' must have only 2 params. Example: '$cmd.commandOrders AIONETH AIONETH' Msg = $message")
                        }
                    }

                    cmd.commandReset.matches(message) -> {
                        msg += "Command '${cmd.commandReset}' not supported yet!"
                    }

                    cmd.commandQueueSize.matches(message) -> {
                        val params = message.split("\\s+".toRegex())
                        msg = if (params.size > 1)
                            "${params[1]} queueSize = ${get(params[1])?.queue?.size}"
                        else
                            "command 'queueSize' must have one param"
                    }

                    cmd.commandSettings.matches(message) -> {
                        val params = message.split("\\n+".toRegex(), limit = 2)
                        val param = params[0].split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1]
                            get(key)?.let { tradePair ->

                                val settings = params[1].deserialize<RatioSetting>()

                                log.info("new settings: $settings\nfor tradePair: $key")

                                tradePair.queue.add(BotEvent(params[1], BotEvent.Type.SET_SETTINGS))

                            } ?: run { msg = "$key not exist" }
                        } else {
                            msg = "command 'settings' must have two param"
                            log.info("command 'settings' must have two param. Msg = $message")
                        }
                    }

                    else -> {
                        msg = "Unsupported command:\n$message\ncommands:\n$cmd"
                        log.info("Unsupported command $message")
                    }
                }
            }
        }

        if (msg.isNotBlank()) return send(msg)
    }

    fun sendOrder(message: String) {

        log.info("sendOrder $message")

        val notification = message.deserialize<Notification>()

        log.info("tradePairs:\n $tradePairs")

        val founded = tradePairs.filter { it.value.botSettings.pair == TradePair(notification.pair) }
//            .forEach { (_, v) -> v.queue.add(BotEvent(message, BotEvent.Type.CREATE_ORDER)) }

        log.info("founded by filter:\n $founded")

        val getByKey = tradePairs["test_ETH_USDT"]

        log.info("get by key test_ETH_USDT:\n $getByKey")

        getByKey?.queue
            ?.add(BotEvent(message, BotEvent.Type.CREATE_ORDER))
            ?: sendMessage("TradePair ${notification.pair} not found", false)
    }

    private fun parseTradeBotSettings(p: List<String>): Pair<String, BotSettings?> {

        var msg = ""
        val (name, msg0) = getBotStartParam(p, "name", msg)
        val (pair, msg1) = getBotStartParam(p, "pair", msg0)
        val (exchange, msg2) = getBotStartParam(p, "exchange", msg1)
        val (ordersTypeStr, msg3) = getBotStartParam(p, "ordersType", msg2)
        val (tradingRangeStr, msg4) = getBotStartParam(p, "tradingRange", msg3)
        val (orderSizeStr, msg5) = getBotStartParam(p, "orderSize", msg4)
        val (orderDistanceStr, msg6) = getBotStartParam(p, "orderDistance", msg5)
        val (triggerDistanceStr, msg7) = getBotStartParam(p, "triggerDistance", msg6)
        val (orderMaxQuantityStr, msg8) = getBotStartParam(p, "orderMaxQuantity", msg7)
        val (directionStr, msg9) = getBotStartParam(p, "direction", msg8)
        val (orderBalanceTypeStr, msg10) = getBotStartParam(p, "orderBalanceType", msg9)
        val (countOfDigitsAfterDotForAmountStr, msg11) = getBotStartParam(p, "countOfDigitsAfterDotForAmount", msg10)
        val (countOfDigitsAfterDotForPriceStr, msg12) = getBotStartParam(p, "countOfDigitsAfterDotForPrice", msg11)
        val (enableStopOrderDistanceStr, msg13) = getBotStartParam(p, "enableStopOrderDistance", msg12)

        msg += msg13

        if (msg.isEmpty()) {

            if (name.matches("^[a-zA-Z0-9_]+$".toRegex()).not()) {
                msg += "Incorrect value 'name': $name should match '^[a-zA-Z0-9_]+$'"
                log.warn("Incorrect value 'name': $name should match '^[a-zA-Z0-9_]+$'")
            }

            val ordersType: TYPE? = try {
                TYPE.valueOf(ordersTypeStr.uppercase())
            } catch (t: Throwable) {
                msg += "Incorrect value 'ordersType': $ordersTypeStr"
                log.warn("Incorrect value 'ordersType': $ordersTypeStr", t)
                null
            }

            val direction: DIRECTION? = try {
                DIRECTION.valueOf(directionStr.uppercase())
            } catch (t: Throwable) {
                msg += "Incorrect value 'direction': $directionStr"
                log.warn("Incorrect value 'direction': $directionStr", t)
                null
            }

            val tradingRange: Pair<BigDecimal, BigDecimal>? = try {
                tradingRangeStr.split("[-\\s,]+".toRegex()).let { it[0].toBigDecimal() to it[1].toBigDecimal() }
            } catch (t: Throwable) {
                msg += "Incorrect value 'tradingRange': $tradingRangeStr"
                log.warn("Incorrect value 'tradingRange': $tradingRangeStr", t)
                null
            }

            val orderSize: BigDecimal? = try {
                orderSizeStr.toBigDecimal()
            } catch (t: Throwable) {
                msg += "Incorrect value 'orderSize': $orderSizeStr"
                log.warn("Incorrect value 'orderSize': $orderSizeStr", t)
                null
            }

            val orderDistance: BigDecimal? = try {
                orderDistanceStr.toBigDecimal()
            } catch (t: Throwable) {
                msg += "Incorrect value 'orderDistance': $orderDistanceStr"
                log.warn("Incorrect value 'orderDistance': $orderDistanceStr", t)
                null
            }

            val triggerDistance: BigDecimal? = try {
                triggerDistanceStr.toBigDecimal()
            } catch (t: Throwable) {
                msg += "Incorrect value 'triggerDistance': $triggerDistanceStr"
                log.warn("Incorrect value 'triggerDistance': $triggerDistanceStr", t)
                null
            }

            val orderMaxQuantity: Int? = try {
                orderMaxQuantityStr.toInt()
            } catch (t: Throwable) {
                msg += "Incorrect value 'orderMaxQuantity': $orderMaxQuantityStr"
                log.warn("Incorrect value 'orderMaxQuantity': $orderMaxQuantityStr", t)
                null
            }

            val countOfDigitsAfterDotForAmount: Int? = try {
                countOfDigitsAfterDotForAmountStr.toInt()
            } catch (t: Throwable) {
                msg += "Incorrect value 'countOfDigitsAfterDotForAmount': $countOfDigitsAfterDotForAmountStr"
                log.warn("Incorrect value 'countOfDigitsAfterDotForAmount': $countOfDigitsAfterDotForAmountStr", t)
                null
            }

            val countOfDigitsAfterDotForPrice: Int? = try {
                countOfDigitsAfterDotForPriceStr.toInt()
            } catch (t: Throwable) {
                msg += "Incorrect value 'countOfDigitsAfterDotForPrice': $countOfDigitsAfterDotForPriceStr"
                log.warn("Incorrect value 'countOfDigitsAfterDotForPrice': $countOfDigitsAfterDotForPriceStr", t)
                null
            }

            val orderBalanceType: String = if (orderBalanceTypeStr == "first" || orderBalanceTypeStr == "second") {
                orderBalanceTypeStr
            } else "second"

            val enableStopOrderDistance: BigDecimal = try {
                enableStopOrderDistanceStr.toBigDecimal()
            } catch (t: Throwable) {
                0.toBigDecimal()
            }

            if (name.isNotBlank()
                && pair.isNotBlank()
                && exchange.isNotBlank()
                && ordersType != null
                && direction != null
                && tradingRange != null
                && orderSize != null
                && orderDistance != null
                && triggerDistance != null
                && orderMaxQuantity != null
                && countOfDigitsAfterDotForAmount != null
                && countOfDigitsAfterDotForPrice != null
            ) {
                return msg to BotSettings(
                    name = name,
                    pair = TradePair(pair),
                    exchange = exchange,
                    direction = direction,
                    ordersType = ordersType,
                    tradingRange = tradingRange,
                    orderSize = orderSize,
                    orderDistance = orderDistance,
                    orderBalanceType = orderBalanceType,
                    triggerDistance = triggerDistance,
                    enableStopOrderDistance = enableStopOrderDistance,
                    orderMaxQuantity = orderMaxQuantity,
                    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
                    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
                )
            }
        }
        return msg to null
    }

    fun getInfo() = tradePairs.values.map { it.botSettings to (it as AlgorithmBobblesIndicator).positions }

    private fun getBotStartParam(params: List<String>, paramName: String, prevMsg: String): Pair<String, String> {
        var msg = prevMsg
        return params.find { it.startsWith(paramName) }?.split("\\s+".toRegex(), 2)?.let {
            if (it.size < 2) {
                msg += "\nEmpty value for: $paramName"
                "" to msg
            } else it[1] to msg
        } ?: run {
            msg += "\nNot found parameter: $paramName"
            "" to msg
        }
    }

    private fun getClient(conf: Config): Client = newClient(
        exchangeEnum = conf.getEnum(ExchangeEnum::class.java, "exchange"),
        api = conf.getString("api"),
        sec = conf.getString("sec")
    )

    private fun getStatistics() {
        var msg = "${cmd.commandStatus}:"
        msg += "\n${convertTime(System.currentTimeMillis())}\n"
        tradePairs.let { pairs ->
            pairs.forEach {

                msg += "\n${it.key} ${
                    if (it.value.state.toString().startsWith("TIMED_")) {
                        it.value.state.toString().drop(6).dropLast(3)
                    } else it.value.state.toString()
                }"

            }
            send(msg)
        }
    }

    private fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)
}