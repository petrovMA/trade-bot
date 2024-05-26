package bot.trade

import bot.trade.database.service.ActiveOrdersService
import bot.trade.database.service.OrderService
import bot.trade.exchanges.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.emulate.TestBalance
import bot.trade.exchanges.libs.TrendCalculator
import bot.trade.libs.*
import bot.trade.rest_controller.Notification
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

class Communicator(
    private val exchangeFiles: File,
    private val exchangeBotsFiles: String,
    private val orderService: OrderService? = null,
    private val activeOrdersService: ActiveOrdersService,
    intervalCandlestick: Duration?,
    intervalStatistic: Duration?,
    timeDifference: Duration?,
    candlestickDataCommandStr: String?,
    private val candlestickDataPath: Map<ExchangeEnum, String>,
    val taskQueue: BlockingQueue<Thread>?,
    private val cmd: Commands = Commands(),
    private val defaultCommands: Map<Regex, String> = mapOf(
        cmd.commandAllBalance to "commandAllBalance BTC ETH BNB",
        cmd.commandBalance to "commandFreeBalance BTC ETH BNB"
    ),
    private val logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null,
    private val sendFile: (File) -> Unit,
    val sendMessage: (String, Boolean) -> Unit
) {
    private val log = KotlinLogging.logger {}

    private val intervalCandlestickUpdate: Duration = intervalCandlestick ?: 2.d()
    private val intervalStatistic: Duration = intervalStatistic ?: 4.h()
    private val timeDifference: Duration = timeDifference ?: 0.ms()

    private var tradeBots: MutableMap<String, Algorithm> = emptyMap<String, Algorithm>().toMutableMap()

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
//        repeatEvery({
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.BINANCE, sendMessage))
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.BITMAX, sendMessage))
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.HUOBI, sendMessage))
//            taskQueue.put(CollectCandlestickData(candlestickDataCommand, startData, ExchangeEnum.GATE, sendMessage))
//        }, this.intervalCandlestickUpdate, this.timeDifference)
//        repeatEvery({ getStatistics() }, this.intervalStatistic, this.timeDifference)

    }

    fun onUpdate(message: String) {
        var msg = ""

        when {
            cmd.commandScan.matches(message) -> {
                msg = File(exchangeBotsFiles)
                    .run {
                        if (exists() && isDirectory) listFiles()
                        else emptyArray()
                    }
                    .joinToString("\n") { it.name }
                msg = "scanned:\n$msg"
                log.info(msg)
            }

            cmd.commandCreate.matches(message) -> {
                val params = message.split("\\n+".toRegex(), limit = 2)

                try {
                    params[1].deserialize<BotSettings>()
                } catch (t: Throwable) {
                    msg = "Incorrect settings format:\n${params[1]}"
                    log.warn("Incorrect settings format:\n${params[1]}", t)
                    null
                }?.let { botSettings ->

                    when (botSettings) {
                        is BotSettingsTrader -> {
                            if (tradeBots[botSettings.name] == null) {
                                tradeBots[botSettings.name] = AlgorithmTrader(
                                    botSettings,
                                    exchangeBotsFiles,
                                    activeOrdersService,
                                    sendMessage = sendMessage
                                )

                                activeOrdersService.deleteByBotName(botSettings.name)

                                log.info("new BotSettingsTrader: $botSettings")

                            } else {
                                msg = "TradePair with name '${botSettings.name}' already exist!"
                                log.info("TradePair with name '${botSettings.name}' already exist!")
                            }
                        }

                        else -> {
                            if (tradeBots[botSettings.name] == null) {
                                tradeBots[botSettings.name] = AlgorithmBobblesIndicator(
                                    botSettings,
                                    exchangeBotsFiles = exchangeBotsFiles,
                                    orderService = orderService,
                                    sendMessage = sendMessage
                                )
                                log.info("new AlgorithmBobblesIndicator: $botSettings")

                            } else {
                                msg = "TradePair with name '${botSettings.name}' already exist!"
                                log.info("TradePair with name '${botSettings.name}' already exist!")
                            }
                        }
                    }
                }
            }

            cmd.commandEmulate.matches(message) -> {
                val params = message.split("\\n+".toRegex(), limit = 2)

                try {
                    params[1].deserialize<BotEmulateParams>()
                } catch (t: Throwable) {
                    msg = "Incorrect settings format:\n${params[1]}"
                    log.warn("Incorrect settings format:\n${params[1]}", t)
                    null
                }?.let { emulateParams ->
                    emulate(emulateParams)
                }
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
                TODO("not implemented yet")
            }

            cmd.commandCreateTradeBot.matches(message) -> {
                TODO("not implemented yet")
            }

            cmd.commandLoadTradeBot.matches(message) -> {

                val params = message.split("\\s+".toRegex())
                val tradeBotSettings =
                    readObjectFromFile(File("$exchangeBotsFiles/${params[1]}/settings.json"), BotSettings::class.java)

                tradeBots[tradeBotSettings.name] = when (tradeBotSettings.type) {
                    "bobbles" -> AlgorithmBobblesIndicator(
                        tradeBotSettings,
                        exchangeBotsFiles,
                        orderService,
                        sendMessage = sendMessage
                    )

                    else -> AlgorithmTrader(
                        tradeBotSettings,
                        exchangeBotsFiles,
                        activeOrdersService,
                        logMessageQueue = logMessageQueue,
                        sendMessage = sendMessage
                    )
                }

                msg += "Trade bot ${tradeBotSettings.name} loaded, settings:\n```json\n${json(tradeBotSettings)}\n```"
                send(msg, true)
                log.info(msg)

                msg = ""
            }

            cmd.commandStartTradeBot.matches(message) -> startBot(message)
            cmd.commandResumeTradeBot.matches(message) -> startBot(message, false)

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
                taskQueue?.put(
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
                taskQueue?.put(
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

            else -> tradeBots.apply {

                when {
                    cmd.commandStatus.matches(message) -> {
                        msg = "${cmd.commandStatus}:\n"
                        forEach { msg += "${it.value.state} => ${it.key}\n" }
                        log.info(msg)
                    }

                    cmd.commandCreateAll.matches(message) -> {
                        msg += "Command '${cmd.commandCreateAll}' not supported yet!"
                    }

                    cmd.commandUpdate.matches(message) -> {
                        val params = message.split("\\n+".toRegex(), limit = 2)

                        try {
                            params[1].deserialize<BotSettings>()
                        } catch (t: Throwable) {
                            msg = "Incorrect settings format:\n${params[1]}"
                            log.warn("Incorrect settings format:\n${params[1]}", t)
                            null
                        }?.let { botSettings ->
                            get(botSettings.name)
                                ?.queue
                                ?.add(BotEvent(json(botSettings), BotEvent.Type.SET_SETTINGS))
                                ?: run {
                                    msg = "TradePair with name '${botSettings.name}' not exist!"
                                    log.info("TradePair with name '${botSettings.name}' not exist!")
                                }
                        }
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
                            val key = param[1]
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
                                it.value.stopThis()
                                msg += it.key
                            }
                        msg = "stopped pairs:\n$msg"
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

                    cmd.commandPause.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1]
                            msg = get(key)?.let { value ->
                                value.queue.add(BotEvent(type = BotEvent.Type.PAUSE))
                                log.info("$key paused")
                                "$key paused"
                            } ?: run {
                                log.info("$key not exist")
                                "$key not exist"
                            }
                        } else {
                            msg = "command 'pause' must have one param"
                            log.info("command 'pause' must have one param. Msg = $message")
                        }
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

                                val settings = params[1].deserialize<BotSettings>()

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

    private fun startBot(message: String, isDeleteOldBotData: Boolean = true): String {

        val param = message.split("\\s+".toRegex())

        val key = param[1]

        val msg = if (param.size == 2) {
            tradeBots[key]?.start()?.let {
                log.info("$key started")
                "$key started"
            } ?: run {
                log.info("$key not exist")
                "$key not exist"
            }
        } else {
            log.info("command 'start' must have one param. Msg = $message")
            "command 'start' must have one param"
        }

        if (isDeleteOldBotData) activeOrdersService.deleteByBotName(key)

        return msg
    }

    fun sendOrder(message: String) {

        log.info("sendOrder $message")

        val notification = message.deserialize<Notification>()

        tradeBots[notification.botName]?.queue
            ?.add(BotEvent(message, BotEvent.Type.CREATE_ORDER))
            ?: sendMessage("TradeBot ${notification.botName} not found", false)
    }

    fun getInfo() = tradeBots.values.map { it.botSettings to (it as AlgorithmBobblesIndicator).positions }

    fun getHedgeModule(botName: String) = tradeBots[botName]?.let {
        if (it is AlgorithmTrader)
            it.calcHedgeModule()
        else
            null
    }

    fun getTrend(botName: String): TrendCalculator.Trend? = tradeBots[botName]?.let { bot ->
        if (bot is AlgorithmTrader) bot.getTrend()
        else null
    }

    fun positions(botName: String): Pair<Position?, Position?>? = tradeBots[botName]?.let { bot ->
        if (bot is AlgorithmTrader) bot.positions()
        else null
    }

    fun orderBorders(botName: String): List<BigDecimal?>? = tradeBots[botName]?.let { bot ->
        if (bot is AlgorithmTrader) bot.orderBorders()
        else null
    }

    fun getBotsList() = tradeBots.map { it.key }

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
        tradeBots.let { pairs ->
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

    fun emulate(params: BotEmulateParams): TestBalance {

        // clear order storage
        activeOrdersService.deleteByBotName(params.botParams.name)

        val test = TestClientFileData(params)


        val trendCalculator: TrendCalculator? = params.botParams.trendDetector?.run {
            TrendCalculator(
                client = test,
                pair = params.botParams.pair,
                hma1 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma1Period,
                hma2 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma2Period,
                hma3 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma3Period,
                rsi1 = rsi1.timeFrame.toDuration() to rsi1.rsiPeriod,
                rsi2 = rsi2.timeFrame.toDuration() to rsi2.rsiPeriod,
                inputKlineInterval = inputKlineInterval?.let { it.toDuration() to it.toInterval() }
                    ?: (5.m() to INTERVAL.FIVE_MINUTES)
            ).also {

                val maxConverterTime = it.getMaxConverterTime()

                val from: ZonedDateTime = params.from?.let { time -> ZonedDateTime.parse(time).minusSeconds(maxConverterTime.ms().toSeconds()) }!!
                val to: ZonedDateTime = params.from.let { time -> ZonedDateTime.parse(time) }

                File("database/${params.botParams.pair}_klines.csv").forEachLine { line ->
                    if (line.isNotBlank()) {
                        val candlestick = Candlestick(line.split(';'), 1.m())
                        if (from == null || from.isBefore(candlestick.openTime.toZonedTime())) {
                            if (to == null || to.isAfter(candlestick.openTime.toZonedTime()))
                                it.addCandlesticks(candlestick)
                        }
                    }
                }
            }
        }


        val algorithm = AlgorithmTrader(
            params.botParams,
            "$exchangeBotsFiles/emulate/${params.botParams.pair}/settings.json",
            activeOrdersService,
            endTimeForTrendCalculator = test.from.toInstant().toEpochMilli(),
            trendCalculator = trendCalculator,
            client = test,
            sendMessage = { _, _ -> }
        )

        algorithm.setup()
        test.handler = { botMessage -> algorithm.handle(botMessage) }

        val result = test.emulate()

        println(result)

        return result
    }

    private fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)
}