package bot.telegram.notificator

import bot.telegram.notificator.exchanges.*
import bot.telegram.notificator.exchanges.emulate.Emulate
import bot.telegram.notificator.exchanges.clients.Client
import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import bot.telegram.notificator.exchanges.clients.newClient
import bot.telegram.notificator.libs.*
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
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
    val sendMessage: (String) -> Unit
) {
    private val log = KotlinLogging.logger {}

//    fun sendMessage(messageText: String) = responseQueue.add(messageText)
//    fun sendFile(resultFile: File) = responseQueue.add(resultFile)

    private val intervalCandlestickUpdate: Duration = intervalCandlestick ?: 2.d()
    private val intervalStatistic: Duration = intervalStatistic ?: 4.h()
    private val timeDifference: Duration = timeDifference ?: 0.ms()
    private var symbols = File("pairsSet").useLines { it.toList() }

    private var propertyPairs: Map<String, String> = scanAll(exchangeFiles, symbols)
    private var tradePairs: MutableMap<String, TraderAlgorithm>? = null

    var candlestickDataCommand: Command = candlestickDataCommandStr?.let {
        try {
            Command.valueOf(it)
        } catch (t: Throwable) {
            Command.NONE
        }
    } ?: Command.NONE

    private val firstDayForCheck: LocalDate? = null

    init {
        log.info("Bot starts!")
        repeatEvery({
            taskQueue.put(CollectCandlestickData(candlestickDataCommand, firstDayForCheck, ExchangeEnum.BINANCE, sendMessage))
            taskQueue.put(CollectCandlestickData(candlestickDataCommand, firstDayForCheck, ExchangeEnum.BITMAX, sendMessage))
            taskQueue.put(CollectCandlestickData(candlestickDataCommand, firstDayForCheck, ExchangeEnum.HUOBI, sendMessage))
        }, this.intervalCandlestickUpdate, this.timeDifference)
        repeatEvery({ getStatistics() }, this.intervalStatistic, this.timeDifference)
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
            cmd.commandShowProp.matches(message) -> {
                propertyPairs[message.toUpperCase().split("\\s+".toRegex()).last()]?.let {
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
                    val cmnd = param[1].toUpperCase()
                    candlestickDataCommand = Command.valueOf(cmnd)
                    msg += "Set CollectCandlestickData command to: $cmnd"
                } else {
                    msg = "command 'candlestick' must have one param"
                    log.info("command 'candlestick' must have one param. Msg = $message")
                }
            }
            cmd.commandEmulate.matches(message) -> {
                val params = message.split("\\s+".toRegex())
                try {
                    taskQueue.put(Emulate(sendFile, sendMessage, params[2].toRegex(), params[3], params[4], candlestickDataPath, ExchangeEnum.valueOf(params[1].toUpperCase())))
                    msg = "Emulate process add to task queue! Symbol = ${params[2]}; Exchange = ${params[1]}"
                    log.info("Emulate process add to task queue! Symbol = ${params[2]}; Exchange = ${params[1]}")
                } catch (t: Throwable) {
                    msg = "Can't parse command:\n$message"

                    log.warn("Can't parse command:\n$message", t)
                    t.printStackTrace()
                }
            }
            cmd.commandDeleteOldCandlestickData.matches(message) -> {
                val params = message.split("\\s+".toRegex())
                taskQueue.put(DeleteOldCandlestickData(ExchangeEnum.valueOf(params[1].toUpperCase()), sendMessage, params[2].toUpperCase()))
                msg = "DeleteOldData command accepted!"
            }
            cmd.commandCollect.matches(message) -> {
                val params = message.split("\\s+".toRegex())
                taskQueue.put(CollectCandlestickData(Command.valueOf(params[2].toUpperCase()), firstDayForCheck, ExchangeEnum.valueOf(params[1].toUpperCase()), sendMessage))
                msg = "Collect Data command accepted!"
            }
            cmd.commandTradePairsInit.matches(message) -> {
                tradePairs = propertyPairs.mapNotNull {
                    readConf(it.value)?.let { conf ->
                        msg += "${it.value} initialized successfully!\n"
                        it.key to TraderAlgorithm(conf, sendMessage = sendMessage)
                    } ?: run {
                        msg += "Config for ${it.value} not found!\n"
                        null
                    }
                }.toMap().toMutableMap()
            }
            else -> tradePairs?.apply {

                when {
                    cmd.commandStatus.matches(message) -> {
                        msg = "${cmd.commandStatus}:\n"
                        forEach { msg += "${it.value.state} => ${it.key}\n" }
                        log.info(msg)
                    }
                    cmd.commandCreateAll.matches(message) -> {
                        any { it.value.isAlive }
                                .let { isAtLeastOneAlive ->
                                    if (isAtLeastOneAlive) {
                                        filter { it.value.isAlive }.forEach { msg += "${it.key}\n" }
                                        msg = "Cannot create pairs:\n${msg}they Alive"
                                        log.info(msg)
                                    } else {
                                        tradePairs = HashMap(propertyPairs.mapValues {
                                            msg += "${it.key}\n"
                                            TraderAlgorithm(readConf(it.value)
                                                    ?: throw ConfigNotFoundException(("Config file not found in: '${it.value}'")), sendMessage = sendMessage)
                                        })
                                        msg = "All properties created:\n$msg"
                                        log.info(msg)
                                    }
                                }
                    }
                    cmd.commandCreate.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key = param[1].toUpperCase()
                            msg = get(key).let { value ->
                                if (value == null) {
                                    try {
                                        set(key, TraderAlgorithm((readConf(propertyPairs[key])
                                                ?: throw ConfigNotFoundException(("Config file not found in: '${propertyPairs[key]}'"))), sendMessage = sendMessage)
                                        )
                                        log.info("$key created")
                                        "$key created"
                                    } catch (e: Exception) {
                                        log.error("Can't create $key", e)
                                        "Can't create $key, error:\n${e.stackTrace}"
                                    }
                                } else {
                                    if (value.isAlive) {
                                        log.info("Cannot create pair:\n$key it's Alive")
                                        "Cannot create pair:\n$key it's Alive"
                                    } else {
                                        log.info("Cannot create, $key already exist")
                                        "Cannot create, $key already exist"
                                    }
                                }
                            }
                        } else {
                            msg = "command 'create' must have one param"
                            log.info("command 'create' must have one param. Msg = $message")
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
                            val key = param[1].toUpperCase()
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
                            val key = param[1].toUpperCase()
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
                                msg = "command 'FreeBalance' must have at least 2 param. Example: 'FreeBalance AIONETH AION WAN'"
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
                                msg = "command 'FreeBalance' must have at least 2 param. Example: 'FreeBalance AIONETH AION WAN'"
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
                        var pairs = ""
                        map {
                            pairs += "${it.key} "
                            it.value
                        }
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
                            val key = param[1].toUpperCase()
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
                            val key = param[1].toUpperCase()
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
                        val param = message.toUpperCase().split("\\s+".toRegex())
                        if (param.size == 3) {
                            val key = param[1].toUpperCase()
                            val trade = get(key) ?: values.first()
                            trade.queue.add(BotEvent(param[2], BotEvent.Type.GET_PAIR_OPEN_ORDERS))
                        } else {
                            msg = "command '$cmd.commandOrders' must have only 2 params. Example: '$cmd.commandOrders AIONETH AIONETH'"
                            log.info("command '$cmd.commandOrders' must have only 2 params. Example: '$cmd.commandOrders AIONETH AIONETH' Msg = $message")
                        }
                    }
                    cmd.commandReset.matches(message) -> {
                        val param = message.split("\\s+".toRegex())
                        if (param.size == 2) {
                            val key: String = param[1].toUpperCase()
                            get(key)?.let { value ->
                                value.queue.add(BotEvent(type = BotEvent.Type.INTERRUPT))
                                log.info("$key stopped")
                                msg += "$key stopped"
                                remove(key)
                                log.info("$key deleted")
                                msg += "\n$key deleted"
                            } ?: run {
                                log.info("$key not exist")
                                msg += "\n$key not exist"
                            }
                            try {
                                set(key, TraderAlgorithm(readConf(propertyPairs[key]
                                        ?: error("Property pair doesn't exist"))
                                        ?: throw ConfigNotFoundException("Config file not found in: ${propertyPairs[key]}"), sendMessage = sendMessage)
                                )
                                log.info("$key created")
                                msg += "\n$key created"

                                msg += if (get(key)?.start() != null) {
                                    log.info("$key started")
                                    "\n$key started"
                                } else {
                                    log.info("$key not exist")
                                    "\nNot started. $key not exist"
                                }
                            } catch (e: Exception) {
                                log.error("Can't create $key", e)
                                msg += "\nCan't create $key, error:\n${e.stackTrace}"
                            }
                        } else {
                            msg = "command 'reset' must have one param"
                            log.info("command 'reset' must have one param. Msg = $message")
                        }
                    }
                    cmd.commandQueueSize.matches(message) -> {
                        val params = message.split("\\s+".toRegex())
                        msg = if (params.size > 1)
                            "${params[1]} queueSize = ${get(params[1])?.queue?.size}"
                        else
                            "command 'queueSize' must have one param"
                    }
                    else -> {
                        msg = "Unsupported command:\n$message\ncommands:\n$cmd"
                        log.info("Unsupported command $message")
                    }
                }
            } ?: run {
                msg = "TradePairs not initialized! Run command: 'tradePairs init'\n\n" +
                        "or Unsupported command:\n$message\ncommands:\n$cmd"
            }
        }

        if (msg.isNotBlank()) return sendMessage(msg)
    }

    private fun getClient(conf: Config): Client = newClient(
            exchangeEnum = conf.getEnum(ExchangeEnum::class.java, "exchange"),
            api = conf.getString("api"),
            sec = conf.getString("sec")
    )

    private fun getStatistics() {
        var msg = "${cmd.commandStatus}:"
        msg += "\n${convertTime(System.currentTimeMillis())}\n"
        tradePairs?.let { pairs ->
            pairs.forEach {

                msg += "\n${it.key} ${
                    if (it.value.state.toString().startsWith("TIMED_")) {
                        it.value.state.toString().drop(6).dropLast(3)
                    } else it.value.state.toString()
                }"

                val buy = it.value.balance.orderB ?: return@forEach
                val sell = it.value.balance.orderS ?: return@forEach

                msg += " ${calcGapPercent(buy, sell)}\n${calcExecuted(buy, sell, it.value.balance.balanceTrade)}\n"
            }
            sendMessage(msg)
        } ?: sendMessage("tradePairs not initialized! Run command: 'tradePairs init'")
    }
}