package bot.telegram.notificator.exchanges

import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import bot.telegram.notificator.libs.*
import mu.KotlinLogging
import java.io.File
import java.lang.RuntimeException

class DeleteOldCandlestickData(
    private val exchangeEnum: ExchangeEnum,
    private val sendMessage: (String, Boolean) -> Unit = { _, _ -> },
    lastDate: String = convertTime(System.currentTimeMillis() - 60000 * 60 * 24 * 14, fileFormat)
) : Thread() {
    private val log = KotlinLogging.logger {}

    private val properties = try {
        when (exchangeEnum) {
            ExchangeEnum.BINANCE -> readConf("collect_binance_candlestick.conf")
                    ?: throw RuntimeException("Can't read Config File!")
            ExchangeEnum.BITMAX -> readConf("collect_bitmax_candlestick.conf")
                    ?: throw RuntimeException("Can't read Config File!")
            else -> throw UnsupportedExchangeException()
        }
    } catch (e: Throwable) {
        log.error(e.message, e)
        null
    }

    private val path = properties?.getString("path_out")!!
    private val lastDay = lastDate.toLocalDate()

    override fun run() {
        try {
            deleteOldCandlestickData(path)
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Error in threads.", t)
            send("#DeleteOldCandlestickData #$exchangeEnum error: \n${printTrace(t)}")
        }
    }

    private fun deleteOldCandlestickData(path: String) {
        val mainDir = File(path)
        if (!mainDir.isDirectory) {
            log.error("File $path not a directory!")
            return
        }

        mainDir
                .listFiles()!!
                .toList()
                .forEach { symbolFile ->
                    if (symbolFile.exists() && symbolFile.isDirectory)
                        symbolFile
                                .listFiles()!!
                                .forEach { directoryPair ->
                                    if (directoryPair.name.split("__")[1].toLocalDate().isBefore(lastDay))
                                        deleteDirectory(directoryPair)
                                    else if (directoryPair.name.split("__")[0].toLocalDate().isBefore(lastDay))
                                        directoryPair.listFiles()!!.forEach {
                                            if (it.name.toLocalDate().isBefore(lastDay)) it.delete()
                                        }
                                }
                    else log.error("Directory for $symbolFile not found")
                }
    }

    private fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)
}