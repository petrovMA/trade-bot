package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import bot.telegram.notificator.libs.readConf
import bot.telegram.notificator.telegram.TelegramBot
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.io.File
import java.util.concurrent.LinkedBlockingDeque


private val log = KotlinLogging.logger {}

fun main() {
    PropertyConfigurator.configure("log4j.properties")

    val exchangeFile = File("exchange")
    val taskExecutor = TaskExecutor(LinkedBlockingDeque())

    val propConf = readConf("common.conf") ?: throw RuntimeException("Can't read Config File!")

    taskExecutor.start()
    try {
        val bot = TelegramBot(
            chatId = propConf.getString("bot_properties.bot.chat_id"),
            botUsername = propConf.getString("bot_properties.bot.bot_name"),
            botToken = propConf.getString("bot_properties.bot.bot_token"),
            defaultCommands = mapOf(),
            intervalCandlestick = propConf.getDuration("bot_properties.exchange.interval_candlestick_update"),
            intervalStatistic = propConf.getDuration("bot_properties.exchange.interval_statistic"),
            timeDifference = propConf.getDuration("bot_properties.exchange.time_difference"),
            candlestickDataCommandStr = propConf.getString("bot_properties.exchange.candlestick_data_command"),
            candlestickDataPath = mapOf(
                ExchangeEnum.BINANCE to propConf.getString("bot_properties.exchange.binance_emulate_data_path")!!,
                ExchangeEnum.BITMAX to propConf.getString("bot_properties.exchange.bitmax_emulate_data_path")!!
            ),
            taskQueue = taskExecutor.getQueue(),
            exchangeFiles = exchangeFile
        )
        TelegramBotsApi(DefaultBotSession::class.java).registerBot(bot)

    } catch (e: Exception) {
        e.printStackTrace()
        log.error(e.message, e)
    }
}
