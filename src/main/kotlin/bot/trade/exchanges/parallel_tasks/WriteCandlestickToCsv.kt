package bot.trade.exchanges.parallel_tasks

import bot.trade.exchanges.clients.ExchangeEnum
import bot.trade.exchanges.clients.ExchangeEnum.Companion.newClient
import bot.trade.exchanges.clients.INTERVAL
import bot.trade.exchanges.clients.TradePair
import bot.trade.libs.*
import java.io.File
import java.util.concurrent.LinkedBlockingDeque


class WriteCandlestickToCsv(
    private val exchangeEnum: ExchangeEnum,
    private val pair: TradePair,
    private val start: Long,
    private val end: Long,
    private val sendFile: (File) -> Unit
) : Thread() {

    override fun run() {

        val resultFile = File("database/${pair}_klines.csv")

        exchangeEnum.newClient().apply {

            val logMessageQueue = LinkedBlockingDeque<CustomFileLoggingProcessor.Message>()
            CustomFileLoggingProcessor(logMessageQueue)

            var minutes =
                getCandlestickBars(pair, INTERVAL.ONE_MINUTE, 500, start = start + 1, end = end)

            minutes
                .reversed()
                .forEach {
                    val line = "${it.openTime};${it.open};${it.high};${it.low};${it.close};${it.volume}"
                    logMessageQueue.add(
                        CustomFileLoggingProcessor.Message(
                            File("database/${pair}_klines.csv"),
                            line,
                            false
                        )
                    )
                }

            do {
                minutes =
                    getCandlestickBars(
                        pair,
                        INTERVAL.ONE_MINUTE,
                        500,
                        start = minutes.first().closeTime,
                        end = null
                    )

                minutes
                    .reversed()
                    .forEach {
                        val line = "${it.openTime};${it.open};${it.high};${it.low};${it.close};${it.volume}"
                        logMessageQueue.add(CustomFileLoggingProcessor.Message(resultFile, line, false))
                    }

            } while (minutes.first().closeTime < System.currentTimeMillis())
        }

        sendFile(resultFile)
    }
}