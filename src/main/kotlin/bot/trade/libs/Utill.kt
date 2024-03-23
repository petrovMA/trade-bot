package bot.trade.libs

import bot.trade.exchanges.clients.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import utils.mapper.Mapper.asFile
import utils.mapper.Mapper.asListObjects
import utils.mapper.Mapper.asObject
import utils.mapper.Mapper.asString
import mu.KotlinLogging
import org.springframework.boot.convert.DurationStyle
import utils.mapper.Mapper.asMapObjects
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit


val format = SimpleDateFormat("yyyy.MM.dd HH:mm:ss").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val formatMinutes = SimpleDateFormat("mm:ss").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val formatDays = SimpleDateFormat("yyyy.MM.dd").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val fileFormat = SimpleDateFormat("yyyy_MM_dd").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val fileFormatMonth = SimpleDateFormat("yyyy_MM").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val fileFormatTime = SimpleDateFormat("yyyy_MM_dd__HH_mm_ss").also { it.timeZone = TimeZone.getTimeZone("UTC") }
private val log = KotlinLogging.logger {}

fun convertTime(time: Long, format_: SimpleDateFormat = format): String = format_.format(Date(time))

fun convertTime(time: LocalDateTime, format_: String = "yyyy_MM_dd"): String =
    time.format(DateTimeFormatter.ofPattern(format_))

@Throws(ParseException::class)
fun String.toLocalDate(pattern: String = "yyyy_MM_dd"): LocalDate =
    LocalDate.parse(this, DateTimeFormatter.ofPattern(pattern))

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun <T> readObjectFromFile(file: File, valueType: Class<T>): T =
    if (file.exists() && !file.isDirectory) asObject(file, valueType)
    else throw Exception("Can't find file: ${file.absolutePath}")

inline fun <reified T> readObject(file: String): T? = File(file).let {
    if (it.exists() && !it.isDirectory) asObject(it, T::class.java)
    else null
}

inline fun <reified T> String.deserialize(): T = asObject(this)

fun readListObjectsFromFile(file: File, type: Type): List<Candlestick> =
    if (file.exists() && !file.isDirectory) asListObjects(file, type)
    else throw Exception("Can't find order file: ${file.absolutePath}")

fun readMapObjectsFromFile(file: File, type: Type): Map<String, Order> =
    if (file.exists() && !file.isDirectory) asMapObjects(file, type)
    else throw Exception("Can't find file: ${file.absolutePath}")

fun readListObjectsFromString(json: String, type: Type): List<Candlestick> = asListObjects(json, type)

fun reWriteObject(obj: Any, file: File) {
    removeFile(file)
    asFile(obj, file)
}

fun writeObject(obj: Any, file: File) = asFile(obj, file)

fun json(obj: Any, pretty: Boolean = true) = asString(obj, pretty)

fun escapeMarkdownV2Text(inputText: String): String = StringBuilder().run {
    for (char in inputText) {
        if (char in listOf('_', '*', '~', '#', '+', '-', '.', '!', '|', '\\')) {
            append('\\')
        }
        append(char)
    }
    toString()
}

fun removeFile(file: File) {
    if (file.exists()) file.delete()
}

fun writeLine(obj: Any, file: File) {
    try {
        if (file.exists())
            Files.write(file.toPath(), ("${asString(obj)}\n").toByteArray(), StandardOpenOption.APPEND)
        else {
            if (file.parentFile.run { !exists() || !isDirectory }) Files.createDirectories(file.parentFile.toPath())
            asFile(obj, file)
            Files.write(file.toPath(), "\n".toByteArray(), StandardOpenOption.APPEND)
        }

    } catch (e: IOException) {
        log.error(e.message, e)
    }
}

fun deleteDirectory(directoryToBeDeleted: File): Boolean {
    val allContents = directoryToBeDeleted.listFiles()
    if (allContents != null)
        for (file in allContents)
            deleteDirectory(file)
    return directoryToBeDeleted.delete()
}

fun BigDecimal.div8(by: BigDecimal): BigDecimal = this.divide(by, 8, RoundingMode.HALF_UP)

fun BigDecimal.percent(amountOfPercents: BigDecimal = 1.0.toBigDecimal()): BigDecimal =
    this.divide(100.toBigDecimal(), 8, RoundingMode.HALF_UP) * amountOfPercents

fun BigDecimal.round(scale: Int = 8): BigDecimal = setScale(scale, RoundingMode.HALF_EVEN)

fun Int.ms(): Duration = Duration.ofMillis(this.toLong())
fun Long.ms(): Duration = Duration.ofMillis(this)

fun Int.s(): Duration = Duration.ofSeconds(this.toLong())
fun Long.s(): Duration = Duration.ofSeconds(this)

fun Int.m(): Duration = Duration.ofMinutes(this.toLong())
fun Long.m(): Duration = Duration.ofMinutes(this)

fun Int.h(): Duration = Duration.ofHours(this.toLong())
fun Long.h(): Duration = Duration.ofHours(this)

fun Int.d(): Duration = Duration.ofDays(this.toLong())
fun Long.d(): Duration = Duration.ofDays(this)

fun time() = System.currentTimeMillis().ms()

fun Duration.format() = convertTime(this.toMillis())

fun Long.toZonedTime() = ZonedDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault());

fun readConf(path: String?): Config? = try {
    path?.run {
        val file = File(this)
        if (file.exists()) ConfigFactory.parseFile(file)
        else null
    }
} catch (t: Throwable) {
    log.error("Can't read config file: '$path'", t)
    null
}

fun <E> List<E>.toArrayList(): ArrayList<E> = ArrayList(this)

fun printTrace(e: Throwable, maxLines: Int = 1): String {
    val writer = StringWriter()
    e.printStackTrace(PrintWriter(writer))
    var lineCounter = 0
    val trace = writer.toString()
    var symbolNum = 0
    while (lineCounter <= maxLines && symbolNum < trace.length - 1) {
        if (trace[symbolNum] == '\n')
            lineCounter++
        symbolNum++
    }
    return trace.substring(0..symbolNum)
}

fun repeatEvery(task: () -> Unit, timeRepeat: Duration, timeDifference: Duration = 0.ms()) =
    Timer().scheduleAtFixedRate(
        object : TimerTask() {
            override fun run() {
                task.invoke()
            }
        },
        timeRepeat.toMillis() - (System.currentTimeMillis() + timeDifference.toMillis()) % timeRepeat.toMillis(),
        timeRepeat.toMillis()
    )

fun calcGapPercent(orderB: Order, orderS: Order): String {
    var result = ""
    val (buyPrice, sellPrice) = (orderB.price ?: 0.toBigDecimal()) to (orderS.price ?: 0.toBigDecimal())
    val percent = ((buyPrice + sellPrice) / 2.toBigDecimal()).percent()

    result += String.format(
        "%.2f",
        ((buyPrice - sellPrice) / percent).let { if (it < BigDecimal(0)) it * BigDecimal(-1) else it })
    result += "\nB ${orderB.side} ${orderB.price} | S ${orderS.side} ${orderS.price}"

    return result
}

fun calcExecuted(orderB: Order, orderS: Order, balanceTrade: BigDecimal): String =
    (balanceTrade.toDouble() / 100).let { percent ->
        "qty oB=${
            String.format(
                "%.1f",
                (orderB.price ?: 0.toBigDecimal()).toDouble() * (orderB.origQty.toDouble() - orderB.executedQty.toDouble()) / percent
            )
        }% " +
                "oS=${
                    String.format(
                        "%.1f",
                        (orderS.price ?: 0.toBigDecimal()).toDouble() * (orderS.origQty.toDouble() - orderS.executedQty.toDouble()) / percent
                    )
                }%"
    }

fun <E> BlockingQueue<E>.poll(time: Duration): E? = this.poll(time.seconds, TimeUnit.SECONDS)

fun wait(time: Duration) = Thread.sleep(time.toMillis())

fun String.toInterval(): INTERVAL = when {
    this == "1m" -> INTERVAL.ONE_MINUTE
    this == "3m" -> INTERVAL.THREE_MINUTES
    this == "5m" -> INTERVAL.FIVE_MINUTES
    this == "15m" -> INTERVAL.FIFTEEN_MINUTES
    this == "30m" -> INTERVAL.HALF_HOURLY
    this == "1h" -> INTERVAL.HOURLY
    this == "2h" -> INTERVAL.TWO_HOURLY
    this == "4h" -> INTERVAL.FOUR_HOURLY
    this == "6h" -> INTERVAL.SIX_HOURLY
    this == "8h" -> INTERVAL.EIGHT_HOURLY
    this == "12h" -> INTERVAL.TWELVE_HOURLY
    this == "1d" -> INTERVAL.DAILY
    this == "3d" -> INTERVAL.THREE_DAILY
    this == "1w" -> INTERVAL.WEEKLY
    this == "1M" -> INTERVAL.MONTHLY
    else -> throw Exception("Not supported CandlestickInterval!")
}

fun String.toDuration() = DurationStyle.detectAndParse(this)

fun format(value: BigDecimal?, locale: Locale? = null): String =
    locale?.let { String.format(it, "%.8f", value) } ?: String.format("%.8f", value)

fun compareBigDecimal(a: BigDecimal?, b: BigDecimal?): Boolean =
    (a == b || (a != null && b != null && a.compareTo(b) == 0))