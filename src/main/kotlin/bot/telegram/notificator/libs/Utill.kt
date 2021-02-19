package bot.telegram.notificator.libs

import bot.telegram.notificator.exchanges.clients.Candlestick
import bot.telegram.notificator.exchanges.clients.Client
import bot.telegram.notificator.exchanges.clients.Order
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.bitmax.api.Mapper.asFile
import io.bitmax.api.Mapper.asListObjects
import io.bitmax.api.Mapper.asObject
import io.bitmax.api.Mapper.asString
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.math.abs


val format = SimpleDateFormat("yyyy.MM.dd HH:mm:ss").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val formatMinutes = SimpleDateFormat("mm:ss").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val formatDays = SimpleDateFormat("yyyy.MM.dd").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val fileFormat = SimpleDateFormat("yyyy_MM_dd").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val fileFormatMonth = SimpleDateFormat("yyyy_MM").also { it.timeZone = TimeZone.getTimeZone("UTC") }
val fileFormatTime = SimpleDateFormat("yyyy_MM_dd__HH_mm_ss").also { it.timeZone = TimeZone.getTimeZone("UTC") }
private val log = KotlinLogging.logger {}
var symbols: List<String> = emptyList()
    set(value) {
        field = if (field.isEmpty()) value else field
    }

fun convertTime(time: Long, format_: SimpleDateFormat = format): String = format_.format(Date(time))

fun convertTime(time: LocalDateTime, format_: String = "yyyy_MM_dd"): String = time.format(DateTimeFormatter.ofPattern(format_))

@Throws(ParseException::class)
fun String.toLocalDate(pattern: String = "yyyy_MM_dd"): LocalDate = LocalDate.parse(this, DateTimeFormatter.ofPattern(pattern))
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun <T> readObjectFromFile(file: File, valueType: Class<T>): T =
        if (file.exists() && !file.isDirectory) asObject(file, valueType)
        else throw Exception("Can't find order file: ${file.absolutePath}")

fun readListObjectsFromFile(file: File, type: Type): List<Candlestick> =
        if (file.exists() && !file.isDirectory) asListObjects(file, type)
        else throw Exception("Can't find order file: ${file.absolutePath}")

fun readListObjectsFromString(json: String, type: Type): List<Candlestick> = asListObjects(json, type)

fun reWriteObject(object_: Any, file: File) {
    removeFile(file)
    asFile(object_, file)
}

fun writeObject(object_: Any, file: File) = asFile(object_, file)

fun removeFile(file: File) {
    if (file.exists()) file.delete()
}

fun writeLine(object_: Any, file: File) {
    try {
        if (file.exists())
            Files.write(file.toPath(), ("${asString(object_)}\n").toByteArray(), StandardOpenOption.APPEND)
        else {
            if (file.parentFile.run { !exists() || !isDirectory }) Files.createDirectories(file.parentFile.toPath())
            asFile(object_, file)
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

fun writeLine(text: String, file: File) {
    try {
        if (file.exists()) {
            log.debug("Write to 'BalanceHistory' text: $text")
            Files.write(file.toPath(), ('\n' + text).toByteArray(), StandardOpenOption.APPEND)
        } else {
            // todo CREATE FILE than write
            log.debug("Create file 'BalanceHistory' and write: $text")
            asFile(text, file)
        }
    } catch (e: IOException) {
        log.error(e.message, e)
    }
}

fun String.toDouble(): Double {
    return java.lang.Double.parseDouble(this.replace(',', '.'))
}

fun Double.percent(amountOfPercents: Double = 1.0): Double = this / 100 * amountOfPercents

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

fun getFreeBalances(client: Client, coins: List<String> = emptyList()) =
        client.getBalances().asSequence().filter { coins.contains(it.asset) }.map { it.asset to it.free }

fun scanAll(directory: File) = directory.listFiles()!!
        .filter {
            it.isDirectory && symbols.contains(it.name)
        }
        .associate {
            it.name to "${directory.path}/${it.name}/exchange.conf"
        }

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

fun repeatEvery(task: () -> Unit, timeRepeat: Duration, timeDifference: Duration = 0.ms()) = Timer().scheduleAtFixedRate(object : TimerTask() {
    override fun run() {
        task.invoke()
    }
}, timeRepeat.toMillis() - (System.currentTimeMillis() + timeDifference.toMillis()) % timeRepeat.toMillis(), timeRepeat.toMillis())

fun calcGapPercent(orderB: Order, orderS: Order): String {
    var result = ""
    val (buyPrice, sellPrice) = orderB.price to orderS.price
    val percent = ((buyPrice + sellPrice) / 2).percent()

    result += String.format("%.2f", abs((buyPrice - sellPrice) / percent))
    result += "\nB ${orderB.side} ${orderB.price} | S ${orderS.side} ${orderS.price}"

    return result
}

fun calcExecuted(orderB: Order, orderS: Order, balanceTrade: Double): String = (balanceTrade / 100.0).let { percent ->
    "qty oB=${String.format("%.1f", orderB.price * (orderB.origQty - orderB.executedQty) / percent)}% " +
            "oS=${String.format("%.1f", orderS.price * (orderS.origQty - orderS.executedQty) / percent)}%"
}

fun <E> LinkedBlockingDeque<E>.poll(time: Duration): E? = this.poll(time.seconds, TimeUnit.SECONDS)

fun wait(time: Duration) = Thread.sleep(time.toMillis())