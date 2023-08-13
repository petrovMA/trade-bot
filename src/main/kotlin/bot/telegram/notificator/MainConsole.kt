package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.libs.readConf
import bot.telegram.notificator.libs.s
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.schedule


fun main() {
    val log = KotlinLogging.logger {}
    PropertyConfigurator.configure("log4j.properties")

    val exchangeFile = File("exchange")
    val exchangeBotsFiles = "exchangeBots"
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
            exchangeBotsFiles = exchangeBotsFiles,
            sendFile = {}
        ) { message, _ -> println(message) }

        java.util.Timer("start2", false).schedule(4.s().toMillis()) {
//            bot.onUpdate("start|name name|pair ETH_USDT|ordersType Market|direction LONG|tradingRange 600.123 2000.5|orderSize 0.05|orderDistance 50|triggerDistance 100|orderMaxQuantity 200|firstBalance 1.2|secondBalance 2000|startDate 2022_06_07|endDate 2022_08_19|exchange binance")
                        bot.onUpdate("start|name name|pair ATOM_BTC|ordersType Market|direction LONG|tradingRange 0.00024 0.0009|orderSize 0.5|orderDistance 0.00001|triggerDistance 0.00002|orderMaxQuantity 200|firstBalance 400|secondBalance 0.5|startDate 2022_02_14|endDate 2022_12_01|exchange binance")
        }

        while (true)
            bot.onUpdate(readlnOrNull().toString())

    } catch (e: Exception) {
        e.printStackTrace()
        log.error(e.message, e)
    }
}
