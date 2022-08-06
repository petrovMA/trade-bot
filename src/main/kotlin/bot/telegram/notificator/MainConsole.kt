package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import bot.telegram.notificator.libs.readConf
import bot.telegram.notificator.libs.s
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.schedule


private val log = KotlinLogging.logger {}

fun main() {
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
//                        ExchangeEnum.BITMAX to properties.getString("bot_properties.exchange.bitmax_emulate_data_path")!!,
//                        ExchangeEnum.HUOBI to properties.getString("bot_properties.exchange.huobi_emulate_data_path")!!,
//                        ExchangeEnum.GATE to properties.getString("bot_properties.exchange.gate_emulate_data_path")!!
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


//        java.util.Timer("emulate", false).schedule(3.s().toMillis()) {
//            bot.onUpdate("Collect gate WRITE")

//            bot.onUpdate("tradePairs init")
//            bot.onUpdate("Emulate binance HBAR_BNB 2021_04_21 2021_05_03")

//            bot.onUpdate("FindParams binance HBAR_BNB 2021_04_03 2021_05_03")

//            bot.onUpdate("FindParams huobi RAI_ETH 2021_03_26 2021_04_26")

//            bot.onUpdate("FindParams binance PUNDIX_ETH 2021_03_30 2021_04_30")

//            bot.onUpdate("FindParams binance AVA_BNB 2021_03_28 2021_04_28")
//            bot.onUpdate("FindParams binance AION_ETH 2021_03_28 2021_04_28")
//            bot.onUpdate("FindParams binance IOTX_ETH 2021_03_28 2021_04_28")
//            bot.onUpdate("FindParams binance HBAR_BNB 2021_03_28 2021_04_28")

//            bot.onUpdate("FindParams binance ETH_BTC 2021_03_27 2021_04_27")

//            bot.onUpdate("FindParams binance BTC_USDT 2021_03_27 2021_04_27")
//            bot.onUpdate("FindParams huobi BTC_USDT 2021_03_27 2021_04_27")
//            bot.onUpdate("FindParams bitmax BTC_USDT 2021_03_27 2021_04_27")
//        }
        java.util.Timer("start2", false).schedule(10.s().toMillis()) {
            bot.onUpdate("start|name name|pair BTC_USDT|ordersType Limit|direction LONG|tradingRange 100 101|orderQuantity 10|triggerDistance 2000|maxTriggerDistance 4000|startDate 2022_07_28|endDate 2022_08_01|exchange binance")
        }
//        java.util.Timer("TradePairs", false).schedule(5.m().toMillis()) {
//            bot.taskQueue.put(CollectCandlestickData(bot.candlestickDataCommand, null, ExchangeEnum.BINANCE, bot.sendMessage))
//            bot.taskQueue.put(CollectCandlestickData(bot.candlestickDataCommand, null, ExchangeEnum.BITMAX, bot.sendMessage))
//        }

        while (true)
            bot.onUpdate(readLine().toString())

    } catch (e: Exception) {
        e.printStackTrace()
        log.error(e.message, e)
    }
}
