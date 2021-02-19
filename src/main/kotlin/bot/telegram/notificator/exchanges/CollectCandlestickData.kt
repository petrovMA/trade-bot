package bot.telegram.notificator.exchanges

import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.libs.*
import com.google.gson.reflect.TypeToken
import io.bitmax.api.Mapper.asObject
import mu.KotlinLogging
import java.io.File
import java.time.*
import java.util.*

fun main() {

    CollectCandlestickData(
            command = Command.WRITE_AND_CHECK,
//            command = Command.WRITE,
//            command = Command.CHECK,
            exchangeEnum = ExchangeEnum.BITMAX
    ) {}.run()
}

class CollectCandlestickData(
    private val command: Command,
    firstDay: LocalDate? = null,
    private val exchangeEnum: ExchangeEnum,
    val sendMessage: (String) -> Unit
) : Thread() {
    private val millisecondsInDay = 60_000 * 60 * 24
    private val maxLimit = 1000
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
    private val markGap = properties?.getString("mark_gap")!!
    private val path = properties?.getString("path_out")!!
    private val ignorePairs: List<TradePair> = properties?.getStringList("ignore_pairs")
            ?.map { TradePair(it) }
            ?: emptyList()

    private val firstDay = firstDay ?: properties?.getString("first_day_for_check")?.run { toLocalDate() }
    ?: LocalDate.MIN

    override fun run() {
        val client = when (exchangeEnum) {
            ExchangeEnum.BINANCE -> BinanceClient()
            ExchangeEnum.BITMAX -> BitmaxClient()
            else -> throw UnsupportedExchangeException()
        }
        try {
            when (command) {
                Command.NONE -> Unit
                Command.CHECK -> {
                    checkCandlesticks(path)
                    sendMessage("#CollectCandlestickData #$exchangeEnum check from date $firstDay done")
                }
                Command.WRITE -> {
                    writeCandlesToOutFile(path, client)
                    sendMessage("#CollectCandlestickData #$exchangeEnum write done")
                }
                Command.WRITE_AND_CHECK -> {
                    writeCandlesToOutFile(path, client)
                    sendMessage("#CollectCandlestickData #$exchangeEnum write done, starts check from date $firstDay")
                    checkCandlesticks(path)
                    sendMessage("#CollectCandlestickData #$exchangeEnum check from date $firstDay done")
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Error in threads.", t)
            sendMessage("#CollectCandlestickData #$exchangeEnum error: \n${printTrace(t)}")
        }
    }

    fun checkCandlesticks(path: String) {
        val mainDir = File(path)
        if (!mainDir.isDirectory) {
            log.error("File $path not a directory!")
            return
        }

        var errMsg = ""

        mainDir
                .listFiles()!!
                .toList()
                .filter {
                    try {
                        !ignorePairs.contains(TradePair(it.name))
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        log.error("Can't parse folder '${it.absolutePath}' to TradePair object!")
                        sendMessage("Can't parse folder '${it.absolutePath}' to TradePair object!")
                        false
                    }
                }
                .forEach { symbolFile ->
                    if (symbolFile.exists() && symbolFile.isDirectory) {

                        var hasGap = false
                        var lastTime = 0L
                        var logOut = ""

                        symbolFile
                                .listFiles()!!
                                .sortedBy { f -> f.name }
                                .filter { (it.name + "_01").toLocalDate().run { isAfter(firstDay) || isEqual(firstDay) } }
                                .forEach { candlestickFile ->

                                    CandlestickListsIterator(candlestickFile, 500).forEach list@{ list ->
                                        if (list.first().openTime.toLocalDate().isBefore(firstDay))
                                            return@list

                                        if (lastTime != 0L && lastTime + 1 != list.first().openTime) {
                                            log.debug("First element $symbolFile not sequence in file ${candlestickFile.name},\n" +
                                                    "Gap between ${convertTime(lastTime + 1)} -- ${convertTime(list.first().openTime)}\nElement:\n" +
                                                    "${list.first()}")
                                            hasGap = true
                                        }

                                        for (i in 0 until list.size - 1) {
                                            if (list[i].closeTime + 1 != list[i + 1].openTime) {
                                                log.debug("$symbolFile array not sequence in file ${candlestickFile.name},\n" +
                                                        "Gap between Elements:\n" +
                                                        "closeTime: ${convertTime(list[i].closeTime + 1)} -- ${list[i]}\nopenTime:  ${convertTime(list[i + 1].openTime)} -- ${list[i + 1]}")
                                                hasGap = true
                                            }
                                        }
                                        lastTime = list.last().closeTime
                                    }


                                    if (hasGap && !symbolFile.name.startsWith(markGap)) {
                                        val logLine = "Pair ${symbolFile.name} has a gap!"
                                        logOut += "\n$logLine"
                                    }
                                    if (!hasGap && symbolFile.name.startsWith(markGap)) {
                                        val logLine = "Pair ${symbolFile.name} hasn't gap!"
                                        logOut += "\n$logLine"
                                    }
                                }

                        if (logOut.isNotEmpty()) {
                            log.error("\n\n++=====================================+\n$logOut\n\n++=====================================++\n")
                            errMsg += logOut
                            if (errMsg.length > 4000) {
                                sendMessage("#CollectCandlestickData #$exchangeEnum: errors\n$errMsg")
                                errMsg = ""
                            }

                        }
                    } else log.error("Directory for $symbolFile not found")
                }

        if (errMsg.isNotBlank()) {
            sendMessage("#CollectCandlestickData #$exchangeEnum: errors\n$errMsg")
            errMsg = ""
        }
    }


    private fun writeCandlesToOutFile(path: String, client: Client) {
        val mainDir = File(path)

        if (!mainDir.isDirectory) {
            removeFile(mainDir)
            mainDir.mkdir()
        }

        client
                .getAllPairs()
                .minus(ignorePairs)
                .forEach { tradePair ->
                    try {
                        writeCandles(
                                candlesticks = client.getCandlestickBars(tradePair, INTERVAL.FIVE_MINUTES, maxLimit),
                                pair = tradePair,
                                path = path
                        )
                    } catch (t: Throwable) {
                        log.warn("Can't write pair: $tradePair Error:", t)
                    }
                }
    }

    private fun writeCandles(candlesticks: List<Candlestick>, pair: TradePair, path: String) {
        val lastTime = File("$path/${pair.first}_${pair.second}")
                .listFiles()
                ?.maxByOrNull { f -> f.name }
                ?.let { lastFile -> asObject(getLastLine(lastFile), Candlestick::class.java) }
                ?.closeTime

        candlesticks.forEach {

            if (lastTime != null && lastTime < it.openTime)
                writeLine(it, File("$path/${pair.first}_${pair.second}/${convertTime(it.openTime, fileFormatMonth)}"))
        }
    }
}


class CandlestickListsIterator(private val candlesticksListFile: File, private val listSize: Int) : Iterator<List<Candlestick>> {

    private val sc = Scanner(candlesticksListFile)
    private val log = KotlinLogging.logger {}
    private var lineCounter = 0
    private var isOpen = true

    override fun hasNext(): Boolean = isOpen && sc.hasNextLine()

    override fun next(): List<Candlestick> {
        var i = 0
        val bf = StringBuffer("[ ")
        try {
            while (i++ < listSize) {
                if (!hasNext()) {
                    sc.close()
                    isOpen = false
                    break
                }
                lineCounter++
                bf.append(sc.nextLine()).append(',')
            }
        } catch (t: Throwable) {
            log.error("File: ${candlesticksListFile.absolutePath}; Line: $$lineCounter can't read!", t)
            throw t
        }

        return readListObjectsFromString(bf.substring(0, bf.length - 1) + ']', object : TypeToken<List<Candlestick>>() {}.type)
    }
}

fun getLastLine(file: File): String {
    var r = ""
    file.forEachLine { r = it }
    return r
}

enum class Command {
    CHECK,
    WRITE,
    WRITE_AND_CHECK,
    NONE
}