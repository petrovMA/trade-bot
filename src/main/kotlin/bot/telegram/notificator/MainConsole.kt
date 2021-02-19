package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import bot.telegram.notificator.libs.readConf
import bot.telegram.notificator.libs.symbols
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator
import java.io.File
import java.util.concurrent.LinkedBlockingDeque


private val log = KotlinLogging.logger {}

fun main() {
    // set static values:
    symbols = File("pairsSet").useLines { it.toList() }
    PropertyConfigurator.configure("log4j.properties")

    val exchangeFile = File("exchange")
    val taskExecutor = TaskExecutor(LinkedBlockingDeque())


    val properties = readConf("common.conf")

    taskExecutor.start()
    try {
        val bot = Communicator(
                intervalCandlestick = properties?.getDuration("bot_properties.exchange.interval_candlestick_update"),
                intervalStatistic = properties?.getDuration("bot_properties.exchange.interval_statistic"),
                timeDifference = properties?.getDuration("bot_properties.exchange.time_difference"),
                candlestickDataCommandStr = properties?.getString("bot_properties.exchange.candlestick_data_command"),
                candlestickDataPath = mapOf(
                        ExchangeEnum.BINANCE to properties?.getString("bot_properties.exchange.binance_emulate_data_path")!!,
                        ExchangeEnum.BITMAX to properties.getString("bot_properties.exchange.bitmax_emulate_data_path")!!
                ),
                taskQueue = taskExecutor.getQueue(),
                exchangeFiles = exchangeFile,
                sendFile = {}
        ) { println(it) }

//        Commands for test
//        java.util.Timer("TradePairs", false).schedule(500) { bot.onUpdate("TradePairs init") }
//        java.util.Timer("start", false).schedule(7000) {
//            println("start ALGO_BTC")
//            bot.onUpdate("start ALGO_BTC")
//        }

        while (true)
            bot.onUpdate(readLine().toString())

    } catch (e: Exception) {
        e.printStackTrace()
        log.error(e.message, e)
    }
}
