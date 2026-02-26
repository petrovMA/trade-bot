package bot.communicator

import bot.communicator.BotType.Companion.newBot
import bot.trade.Commands
import bot.trade.database.service.ActiveOrdersService
import bot.trade.database.service.OrderService
import bot.trade.exchanges.Algorithm
import bot.trade.exchanges.AlgorithmBobblesIndicator
import bot.trade.exchanges.AlgorithmGrid
import bot.trade.exchanges.AlgorithmTrader
import bot.trade.exchanges.BotEvent
import bot.trade.exchanges.Command
import bot.trade.exchanges.GridOrders
import bot.trade.exchanges.clients.Candlestick
import bot.trade.exchanges.clients.Client
import bot.trade.exchanges.clients.ExchangeEnum
import bot.trade.exchanges.clients.ExchangeEnum.Companion.newClient
import bot.trade.exchanges.clients.SIDE
import bot.trade.exchanges.clients.INTERVAL
import bot.trade.exchanges.clients.Position
import bot.trade.exchanges.clients.TestClientFileData
import bot.trade.exchanges.clients.TradePair
import bot.trade.exchanges.emulate.TestBalance
import bot.trade.exchanges.libs.TrendCalculator
import bot.trade.exchanges.parallel_tasks.CollectCandlestickData
import bot.trade.exchanges.parallel_tasks.DeleteOldCandlestickData
import bot.trade.exchanges.parallel_tasks.EmulateFromFile
import bot.trade.exchanges.parallel_tasks.WriteCandlestickToCsv
import bot.trade.exchanges.params.BotEmulateParams
import bot.trade.exchanges.params.BotSettings
import bot.trade.exchanges.params.BotSettingsGrid
import bot.trade.exchanges.params.BotSettingsTrader
import bot.trade.libs.CustomFileLoggingProcessor
import bot.trade.libs.convertTime
import bot.trade.libs.d
import bot.trade.libs.deserialize
import bot.trade.libs.h
import bot.trade.libs.json
import bot.trade.libs.m
import bot.trade.libs.ms
import bot.trade.libs.readObjectFromFile
import bot.trade.libs.toDuration
import bot.trade.libs.toInterval
import bot.trade.libs.toZonedTime
import bot.trade.rest_controller.Notification
import bot.trade.rest_controller.MainController
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

class Communicator(
    private val exchangeFiles: File,
    private val exchangeBotsFiles: String,
    private val orderService: OrderService? = null,
    private val activeOrdersService: ActiveOrdersService,
    config: Config,
    botType: BotType,
    intervalCandlestick: Duration?,
    intervalStatistic: Duration?,
    timeDifference: Duration?,
    candlestickDataCommandStr: String?,
    val taskQueue: BlockingQueue<Thread>?,
    private val cmd: Commands = Commands(),
    private val defaultCommands: Map<Regex, String> = mapOf(
        cmd.commandAllBalance to "commandAllBalance BTC ETH BNB",
        cmd.commandBalance to "commandFreeBalance BTC ETH BNB"
    ),
    private val logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null
) {
    private val log = KotlinLogging.logger {}
    private val bot: Bot = botType.newBot(this, config)
    private val sendFile: (File) -> Unit = { bot.sendFile(it) }
    private val sendMessage: (String, Boolean) -> Unit = { text, isMarkdown -> bot.sendMessage(text, isMarkdown) }

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
        send("Server started. Trading bots are not running. Use /start to launch them.")
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

            cmd.commandEmulate.matches(message) -> {
                val params = message.split("\\n+".toRegex(), limit = 2)

                try {
                    params[1].deserialize<BotEmulateParams>()
                } catch (t: Throwable) {
                    msg = "Incorrect settings format:\n${params[1]}"
                    log.warn("Incorrect settings format:\n${params[1]}", t)
                    null
                }?.let { emulateParams ->
                    taskQueue?.put(EmulateFromFile(sendMessage, sendFile, emulateParams, this))
                    msg = ""
                }
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

                // Check if bot with this name already exists
                val existingBot = tradeBots[tradeBotSettings.name]
                if (existingBot != null) {
                    val status = if (existingBot.isAlive) "running" else "loaded"
                    msg = "⚠️ Bot ${tradeBotSettings.name} already exists ($status). Use 'delete ${tradeBotSettings.name}' first."
                    send(msg, false)
                    log.warn(msg)
                } else {
                    tradeBots[tradeBotSettings.name] = when (tradeBotSettings.type) {
                        "AlgorithmBobblesIndicator" -> AlgorithmBobblesIndicator(
                            tradeBotSettings,
                            exchangeBotsFiles,
                            orderService,
                            sendMessage = sendMessage
                        )

                        "AlgorithmGrid" -> AlgorithmGrid(
                            tradeBotSettings,
                            exchangeBotsFiles,
                            activeOrdersService,
                            logMessageQueue = logMessageQueue,
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
                }

                msg = ""
            }

            cmd.commandStartTradeBot.matches(message) -> {
                msg = startBot(message)
            }

            cmd.commandResumeTradeBot.matches(message) -> {
                msg = startBot(message, false)
            }

            cmd.commandForceSyncTradeBot.matches(message) -> {
                msg = forceSyncBot(message)
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

            cmd.writeCandlestickToCsv.matches(message) -> {
                val params = message.split("\\n+".toRegex())

                try {
                    taskQueue?.put(
                        WriteCandlestickToCsv(
                            exchangeEnum = ExchangeEnum.BYBIT,
                            pair = TradePair(params[1]),
                            start = LocalDateTime
                                .parse(params[2], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                .toInstant(ZoneOffset.UTC)
                                .toEpochMilli(),
                            end = LocalDateTime
                                .parse(params[3], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                .toInstant(ZoneOffset.UTC)
                                .toEpochMilli(),
                            sendFile = sendFile
                        )
                    )

                    msg = "WriteCandlestickToCsv command accepted!"

                } catch (t: Throwable) {
                    msg = "Error reading Candlestick data!\n" +
                            "check input message format, example:\n" +
                            "BTC_USDT\n" +
                            "2024-12-16 00:00:00\n" +
                            "2024-12-18 00:00:00"
                }
            }

            cmd.commandTradePairsInit.matches(message) -> {
                msg += "Command '${cmd.commandTradePairsInit}' not supported yet!"
            }

            else -> tradeBots.apply {

                when {
                    cmd.commandStatus.matches(message) -> {
                        msg = "${cmd.commandStatus}:\n"
                        forEach { msg += "${it.value.state} => ${it.key}\n" }

                        taskQueue?.toList()?.let { activeTasks ->
                            if (activeTasks.isNotEmpty())
                                msg += "\n\nActive Tasks:"

                            activeTasks.forEach {
                                msg += when (it) {
                                    is EmulateFromFile -> "\nemulate: ${it.emulateParams.botParams.pair} " +
                                            "${it.emulateParams.from} - ${it.emulateParams.to}"

                                    else -> "unknown task"
                                }
                            }
                        }

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
                            } ?: logNotExist(key)
                        } else {
                            msg = "command 'start' must have one param"
                            log.info("command 'start' must have one param. Msg = {}", message)
                        }
                    }

                    cmd.commandCalcGap.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1].uppercase()
                            msg = get(key)?.run {
                                this.queue.add(BotEvent(message, BotEvent.Type.SHOW_GAP))
                                return
                            } ?: logNotExist(key)
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
                            } ?: logNotExist(key)
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
                            } ?: logNotExist(key)
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
                                // Wait up to 30 seconds for graceful shutdown
                                value.join(30_000)
                                if (value.isAlive) {
                                    log.warn("$key did not stop gracefully within 30s, forcing interrupt")
                                    value.interrupt()
                                    value.join(5_000)  // Wait additional 5s after interrupt
                                }
                                log.info("$key stopped")
                                remove(key)
                                log.info("$key deleted")
                                "$key stopped and deleted"
                            } ?: logNotExist(key)
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

    fun onUpdateWithResult(message: String): String {
        var msg = ""

        when {
            cmd.commandStartTradeBot.matches(message) -> {
                msg = startBot(message)
            }

            cmd.commandResumeTradeBot.matches(message) -> {
                msg = startBot(message, isDeleteOldBotData = false)
            }

            cmd.commandForceSyncTradeBot.matches(message) -> {
                msg = forceSyncBot(message)
            }

            cmd.commandLoadTradeBot.matches(message) -> {
                val params = message.split("\\s+".toRegex())
                val tradeBotSettings =
                    readObjectFromFile(File("$exchangeBotsFiles/${params[1]}/settings.json"), BotSettings::class.java)

                // Check if bot with this name already exists
                val existingBot = tradeBots[tradeBotSettings.name]
                if (existingBot != null) {
                    val status = if (existingBot.isAlive) "running" else "loaded"
                    msg = "Bot ${tradeBotSettings.name} already exists ($status). Use 'delete ${tradeBotSettings.name}' first."
                } else {
                    tradeBots[tradeBotSettings.name] = when (tradeBotSettings.type) {
                        "AlgorithmBobblesIndicator" -> AlgorithmBobblesIndicator(
                            tradeBotSettings,
                            exchangeBotsFiles,
                            orderService,
                            sendMessage = sendMessage
                        )

                        "AlgorithmGrid" -> AlgorithmGrid(
                            tradeBotSettings,
                            exchangeBotsFiles,
                            activeOrdersService,
                            logMessageQueue = logMessageQueue,
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

                    msg = "Trade bot ${tradeBotSettings.name} loaded successfully"
                }
            }

            else -> {
                // For other commands, just execute them normally
                onUpdate(message)
                msg = "Command processed"
            }
        }

        return msg
    }

    private fun forceSyncBot(message: String): String {
        val param = message.split("\\s+".toRegex())
        if (param.size != 2) return "Usage: /forcesync <botName>"
        val key = param[1]
        val bot = tradeBots[key] ?: return "Bot '$key' not found. Is it loaded?"
        if (!bot.isAlive) return "Bot '$key' is not running. Use /resume $key first."
        bot.queue.add(BotEvent("force_sync", BotEvent.Type.FORCE_SYNC))
        return "⚡ Force sync queued for '$key'. Check Telegram/logs for result."
    }

    private fun startBot(message: String, isDeleteOldBotData: Boolean = true): String {

        val param = message.split("\\s+".toRegex())

        val msg = if (param.size == 2) {

            val key = param[1]

            tradeBots[key]?.let { bot ->

                // Check if bot is already running
                if (bot.isAlive) {
                    return@let "⚠️ Bot $key is already running. Use 'stop $key' or 'delete $key' first."
                }

                if (isDeleteOldBotData) {
                    if (bot.botSettings is BotSettingsGrid) {
                        // STEP 1: Check balance FIRST before cancelling any orders
                        log.debug("Current price for {}: {}", bot.botSettings.pair, bot.currentPrice)

                        val firstBalance = bot.client.getBalance(bot.botSettings.pair.first)
                            ?.free ?: run { return@let "Can't receive firstBalance from exchange" }

                        val secondBalance = bot.client.getBalance(bot.botSettings.pair.second)
                            ?.free ?: run { return@let "Can't receive secondBalance from exchange" }

                        log.debug(
                            "First balance ({}): {}, Second balance ({}): {}",
                            bot.botSettings.pair.first,
                            firstBalance,
                            bot.botSettings.pair.second,
                            secondBalance
                        )

                        val gridOrders = GridOrders(bot.currentPrice, bot.botSettings)
                        val reqBalances = gridOrders.calculateRequiredBalance()

                        var balanceMessage = ""

                        val leverageInfo = bot.botSettings.leverage?.takeIf { it > BigDecimal.ZERO }?.let {
                            " (with ${it}x leverage)"
                        } ?: ""

                        if (reqBalances.requiredFirst > firstBalance)
                            balanceMessage += "Insufficient ${reqBalances.firstToken} balance. " +
                                    "Required: ${reqBalances.requiredFirst}${leverageInfo}, Available: $firstBalance \n\n"

                        if (reqBalances.requiredSecond > secondBalance)
                            balanceMessage += "Insufficient ${reqBalances.secondToken} balance. " +
                                    "Required: ${reqBalances.requiredSecond}${leverageInfo}, Available: $secondBalance"

                        // IMPORTANT: Return early if balance is insufficient - don't cancel existing orders!
                        if (balanceMessage.isNotBlank()) {
                            log.warn("Balance check failed, NOT cancelling existing orders: $balanceMessage")
                            return@let balanceMessage
                        }

                        // STEP 2: Balance is sufficient - now cancel existing orders on exchange
                        log.info("Balance check passed. Cancelling existing orders on exchange for ${bot.botSettings.pair}")
                        try {
                            val existingOrders = bot.client.getOpenOrders(bot.botSettings.pair)
                            existingOrders.forEach { order ->
                                try {
                                    bot.client.cancelOrder(bot.botSettings.pair, order.orderId)
                                    log.info("Cancelled order ${order.orderId}")
                                } catch (e: Exception) {
                                    log.warn("Failed to cancel order ${order.orderId}: ${e.message}")
                                }
                            }
                            log.info("Cancelled ${existingOrders.size} orders on exchange")
                        } catch (e: Exception) {
                            log.error("Failed to get/cancel existing orders: ${e.message}", e)
                        }

                        // STEP 3: Delete orders from database and save new grid
                        activeOrdersService.deleteByBotName(key)
                        activeOrdersService.saveAll(gridOrders.orders)

                    } else {
                        // Non-grid bot: just delete from DB
                        activeOrdersService.deleteByBotName(key)
                    }
                } else if (bot.botSettings is BotSettingsGrid) {
                    // Resume: check balance for pending orders (orderId=null, not yet sent to exchange)
                    val gridSettings = bot.botSettings
                    val orders = activeOrdersService.getOrders(key, gridSettings.direction)
                    val pendingOrders = orders.filter { it.orderId == null }

                    if (pendingOrders.isNotEmpty()) {
                        val requiredFirst = pendingOrders
                            .filter { it.orderSide == SIDE.SELL }
                            .sumOf { it.amount ?: BigDecimal.ZERO }

                        val requiredSecond = pendingOrders
                            .filter { it.orderSide == SIDE.BUY }
                            .sumOf { (it.amount ?: BigDecimal.ZERO) * (it.price ?: BigDecimal.ZERO) }

                        val leverage = gridSettings.leverage?.takeIf { it > BigDecimal.ZERO }
                        val adjustedFirst = if (leverage != null) requiredFirst.divide(leverage, 8, RoundingMode.CEILING) else requiredFirst
                        val adjustedSecond = if (leverage != null) requiredSecond.divide(leverage, 8, RoundingMode.CEILING) else requiredSecond

                        val firstBalance = bot.client.getBalance(gridSettings.pair.first)
                            ?.free ?: run { return@let "Can't receive firstBalance from exchange" }

                        val secondBalance = bot.client.getBalance(gridSettings.pair.second)
                            ?.free ?: run { return@let "Can't receive secondBalance from exchange" }

                        var balanceMessage = ""

                        val leverageInfo = leverage?.let { " (with ${it}x leverage)" } ?: ""

                        if (adjustedFirst > firstBalance)
                            balanceMessage += "Insufficient ${gridSettings.pair.first} balance for ${pendingOrders.count { it.orderSide == SIDE.SELL }} pending SELL orders. " +
                                    "Required: $adjustedFirst$leverageInfo, Available: $firstBalance \n\n"

                        if (adjustedSecond > secondBalance)
                            balanceMessage += "Insufficient ${gridSettings.pair.second} balance for ${pendingOrders.count { it.orderSide == SIDE.BUY }} pending BUY orders. " +
                                    "Required: $adjustedSecond$leverageInfo, Available: $secondBalance"

                        if (balanceMessage.isNotBlank()) {
                            log.warn("Resume balance check failed for $key: $balanceMessage")
                            return@let "⚠️ Resume blocked — not enough balance for ${pendingOrders.size} pending orders:\n\n$balanceMessage"
                        }
                    }
                }

                bot.start()

                log.info("$key started!")
                "$key started"
            } ?: logNotExist(key)
        } else {
            log.info("command 'start' must have one param. Msg = {}", message)
            "command 'start' must have one param"
        }

        return msg
    }

    fun sendOrder(message: String) {

        log.info("sendOrder $message")

        val notification = message.deserialize<Notification>()

        tradeBots[notification.botName]?.queue
            ?.add(BotEvent(message, BotEvent.Type.CREATE_ORDER))
            ?: sendMessage("TradeBot ${notification.botName} not found", false)
    }

    fun getInfo(): List<MainController.BotInfoResponse> = tradeBots.values.map { algorithm ->
        val positionData = when (algorithm) {
            is AlgorithmGrid -> {
                // Grid bots don't have position tracking yet
                null
            }
            is AlgorithmTrader -> {
                // Get position from trader bot
                val positions = algorithm.positions()
                positions?.first?.let { longPos ->
                    MainController.PositionResponse(
                        pair = algorithm.botSettings.pair.toString(),
                        marketPrice = longPos.marketPrice.toDouble(),
                        unrealisedPnl = longPos.unrealisedPnl.toDouble(),
                        realisedPnl = longPos.realisedPnl.toDouble(),
                        entryPrice = longPos.entryPrice.toDouble(),
                        breakEvenPrice = longPos.breakEvenPrice.toDouble(),
                        leverage = longPos.leverage.toInt(),
                        liqPrice = longPos.liqPrice.toDouble(),
                        size = longPos.size.toDouble(),
                        side = longPos.side
                    )
                }
            }
            is AlgorithmBobblesIndicator -> {
                // Map VirtualPositions to PositionResponse
                val vPos = algorithm.positions
                MainController.PositionResponse(
                    pair = algorithm.botSettings.pair.toString(),
                    marketPrice = 0.0, // VirtualPositions doesn't have this
                    unrealisedPnl = 0.0, // Would need calculation
                    realisedPnl = 0.0,
                    entryPrice = vPos.buyPrice.toDouble(),
                    breakEvenPrice = 0.0,
                    leverage = 1,
                    liqPrice = 0.0,
                    size = vPos.buyAmount.toDouble(),
                    side = "LONG"
                )
            }
            else -> null
        }

        MainController.BotInfoResponse(
            settings = algorithm.botSettings,
            position = positionData
        )
    }

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

    private fun getClient(conf: Config): Client = conf.getEnum(ExchangeEnum::class.java, "exchange").newClient(
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

    fun emulate(params: BotEmulateParams): Pair<TestBalance, File?> {

        // clear order storage
        activeOrdersService.deleteByBotName(params.botParams.name)

        val test = TestClientFileData(params, activeOrdersService)

        val algorithm = when (params.botParams) {
            is BotSettingsGrid -> AlgorithmGrid(
                params.botParams,
                "$exchangeBotsFiles/emulate/${params.botParams.pair}/settings.json",
                activeOrdersService,
                isEmulate = true,
                client = test,
                sendMessage = { _, _ -> }
            )

            is BotSettingsTrader -> {
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

                        val from: ZonedDateTime? = params.from?.let { time ->
                            ZonedDateTime.parse(time).minusSeconds(maxConverterTime.ms().toSeconds())
                        }
                        val to: ZonedDateTime? = params.from.let { time -> ZonedDateTime.parse(time) }

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

                AlgorithmTrader(
                    params.botParams,
                    "$exchangeBotsFiles/emulate/${params.botParams.pair}/settings.json",
                    activeOrdersService,
                    isEmulate = true,
                    endTimeForTrendCalculator = test.from.toInstant().toEpochMilli(),
                    trendCalculator = trendCalculator,
                    client = test,
                    sendMessage = { _, _ -> }
                )
            }

            else -> throw RuntimeException("Unsupported bot settings type!")
        }

        algorithm.setup()
        test.handler = { botMessage -> algorithm.handle(botMessage) }

        return test.emulate(isWriteOrdersToLog = params.isWriteOrdersToLog ?: false)
    }

    private fun send(message: String, isMarkDown: Boolean = false) {
        try {
            sendMessage(message, isMarkDown)
        } catch (e: Exception) {
            log.error(e) { "Error sending message: $message" }
        }
    }

    private fun logNotExist(key: String) = run {
        log.info("$key not exist")
        "$key not exist"
    }
}