package bot.telegram.notificator.exchanges

import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.libs.*
import mu.KLogger
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator
import org.sqlite.SQLiteException
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.*


fun main() {

    PropertyConfigurator.configure("log4j.properties")

    CollectCandlestickData(
        command = Command.WRITE_AND_CHECK,
//            command = Command.WRITE,
//            command = Command.CHECK,
        exchangeEnum = ExchangeEnum.BINANCE
    ) {}.run()


//        .fromTextToDataBase(
//            "/home/asus/Blockchain/CandlestickData/BITMAX.db",
//            "/home/asus/Blockchain/CandlestickData/BITMAX/BTT_BTC/2021_01"
//        )

}

class CollectCandlestickData(
    private val command: Command,
    firstDay: LocalDate? = null,
    private val exchangeEnum: ExchangeEnum,
    val sendMessage: (String) -> Unit
) : Thread() {
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

    private val pathDB = properties?.getString("path_db")!!
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
                    checkCandlesticks(pathDB)
                    sendMessage("#CollectCandlestickData #$exchangeEnum check from date $firstDay done")
                }
                Command.WRITE -> {
                    writeCandlesToDB(pathDB, client)
                    sendMessage("#CollectCandlestickData #$exchangeEnum write done")
                }
                Command.WRITE_AND_CHECK -> {
                    writeCandlesToDB(pathDB, client)
                    sendMessage("#CollectCandlestickData #$exchangeEnum write done, starts check from date $firstDay")
                    checkCandlesticks(pathDB)
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

        val connect = connect(path, log)

        val stmt = connect.createStatement()
        val resultSetTables = stmt.executeQuery("SELECT name FROM sqlite_master where type='table'")

        var errMsg = ""

        while (resultSetTables.next()) {
            val tableName: String = resultSetTables.getString("name")
            val statementCandles = connect.createStatement()

            var logOut = ""

            val resultSetCandlestick = statementCandles.executeQuery("SELECT * FROM $tableName ORDER BY ID_OPEN_TIME")

            var prevCandlestick: CandlestickDB? = null
            var hasGap = false

            while (resultSetCandlestick.next()) {
                val candlestick = getCandle(resultSetCandlestick)

                prevCandlestick?.run {
                    if (openTime != candlestick.openTime - 300_000) {
                        log.debug(
                            "$tableName table not sequence, Gap between Elements: " +
                                    "${convertTime(openTime)} -- ${convertTime(candlestick.openTime)}"
                        )
                        hasGap = true
                    }
                }

                prevCandlestick = candlestick
            }
            if (hasGap)
                logOut += "\nTable $tableName has a gap!"

            if (logOut.isNotEmpty()) {
                log.error("\n\n++===========================+\n$logOut\n\n++===========================++\n")
                errMsg += logOut
                if (errMsg.length > 4000) {
                    sendMessage("#CollectCandlestickData #$exchangeEnum: errors\n$errMsg")
                    errMsg = ""
                }

            }

            statementCandles.close()
        }
        stmt.close()
        connect.close()

        if (errMsg.isNotBlank())
            sendMessage("#CollectCandlestickData #$exchangeEnum: errors\n$errMsg")
    }


    private fun writeCandlesToDB(pathDB: String, client: Client) = client
        .getAllPairs()
        .minus(ignorePairs)
        .forEach { tradePair ->

            log.trace { "Write pair: $tradePair" }

            try {
                writeCandlesDB(
                    candlesticks = client.getCandlestickBars(tradePair, INTERVAL.FIVE_MINUTES, maxLimit),
                    pair = tradePair,
                    pathDB = pathDB
                )
            } catch (t: Throwable) {
                sendMessage("Can't write pair: $tradePair Error:\n${printTrace(t)}")
                log.warn("Can't write pair: $tradePair Error:", t)
            }
        }


    private fun writeCandlesDB(candlesticks: List<Candlestick>, pair: TradePair, pathDB: String) {
        if (candlesticks.isEmpty()) {
            log.debug { "No data: $pair" }
            return
        }

        val connect = connect(pathDB, log)
        val tableName = "CANDLESTICK_$pair"

        try {
            val candlesticksToDB = getLastRecord(connect, tableName)?.let { last ->
                candlesticks.filter { it.openTime > last.openTime }
            } ?: run {
                createTable(connect, tableName)
                candlesticks
            }

            val stmt = connect.createStatement()
            try {

                var count = 0
                var values = ""

                for (c in 0 until candlesticksToDB.size - 1) {

                    if (++count > 100) {
                        values += """(${candlesticksToDB[c].openTime}, ${candlesticksToDB[c].open}, ${candlesticksToDB[c].close}, ${candlesticksToDB[c].high}, ${candlesticksToDB[c].low}, ${candlesticksToDB[c].volume});"""
                        stmt.executeUpdate("""INSERT INTO $tableName (ID_OPEN_TIME, OPEN, CLOSE, HIGH, LOW, VOLUME) VALUES $values""")
                        count = 0
                        values = ""
                    } else
                        values += """(${candlesticksToDB[c].openTime}, ${candlesticksToDB[c].open}, ${candlesticksToDB[c].close}, ${candlesticksToDB[c].high}, ${candlesticksToDB[c].low}, ${candlesticksToDB[c].volume}),"""
                }

                values += """(${candlesticksToDB.last().openTime}, ${candlesticksToDB.last().open}, ${candlesticksToDB.last().close}, ${candlesticksToDB.last().high}, ${candlesticksToDB.last().low}, ${candlesticksToDB.last().volume});"""

                stmt.executeUpdate("""INSERT INTO $tableName (ID_OPEN_TIME, OPEN, CLOSE, HIGH, LOW, VOLUME) VALUES $values""")


            } catch (t: Throwable) {
                stmt.close()
                log.error("Statement Write Candles to DB ERROR:", t)
            } finally {
                stmt.close()
            }
        } catch (t: Throwable) {
            log.error("Connection Write Candles to DB ERROR:", t)
            connect.close()
        } finally {
            connect.close()
        }
    }


    private fun getLastRecord(connect: Connection, tableName: String): CandlestickDB? {

        val stmt = connect.createStatement()

        var lastCandlestick: CandlestickDB? = null

        try {
            lastCandlestick =
                getCandle(stmt.executeQuery("SELECT * FROM $tableName ORDER BY ID_OPEN_TIME DESC LIMIT 1"))
        } catch (e: SQLiteException) {
            log.info("Can't find table: $tableName")
        } catch (t: Throwable) {
            log.info("Error:\n", t)
        } finally {
            stmt.close()
            return lastCandlestick
        }
    }


    private fun createTable(connect: Connection, tableName: String) {
        val stmt = connect.createStatement()

        stmt.executeUpdate(
            """CREATE TABLE $tableName (
                        ID_OPEN_TIME INTEGER PRIMARY KEY NOT NULL,
                        OPEN         REAL NOT NULL,
                        CLOSE        REAL NOT NULL,
                        HIGH         REAL NOT NULL,
                        LOW          REAL NOT NULL,
                        VOLUME       REAL NOT NULL
                        )"""
        )

        stmt.close()
    }
}


class CandlestickListsIterator(
    connect: Connection,
    private val tableName: String,
    private val listSize: Int,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
) : Iterator<List<Candlestick>> {

    private val stmt = connect.createStatement()
    private val resultSet: ResultSet = stmt.executeQuery(
        """SELECT * FROM $tableName WHERE 
          ID_OPEN_TIME > ${startDateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()} AND
          ID_OPEN_TIME < ${endDateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()} ORDER BY ID_OPEN_TIME"""
    )
    private val log = KotlinLogging.logger {}
    private var hasNext = true

    override fun hasNext(): Boolean = hasNext

    override fun next(): List<Candlestick> {
        val resultList = ArrayList<Candlestick>()
        var i = 0
        try {
            var next: Boolean
            while (resultSet.next().apply { next = this } && i++ < listSize)
                resultList.add(getCandle(resultSet).toCandlestick())

            if (!next) {
                stmt.close()
                hasNext = false
            } else
                resultList.add(getCandle(resultSet).toCandlestick())

        } catch (t: Throwable) {
            log.error("Table: $tableName can't read!", t)
            throw t
        }

        return resultList
    }
}


private fun getCandle(result: ResultSet) = CandlestickDB(
    openTime = result.getLong("ID_OPEN_TIME"),
    open = result.getBigDecimal("OPEN"),
    close = result.getBigDecimal("CLOSE"),
    high = result.getBigDecimal("HIGH"),
    low = result.getBigDecimal("LOW"),
    volume = result.getBigDecimal("VOLUME")
)


fun connect(pathDB: String, log: KLogger): Connection = try {
    Class.forName("org.sqlite.JDBC")
    DriverManager.getConnection("jdbc:sqlite:$pathDB")
} catch (t: Throwable) {
    log.error("Connect Error: {}", t)
    throw t
}


data class CandlestickDB(
    val openTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal
) {
    fun toCandlestick(): Candlestick = Candlestick(
        open = this.open,
        close = this.close,
        openTime = this.openTime,
        closeTime = this.openTime + 299_999,
        high = this.high,
        low = this.low,
        volume = this.volume
    )
}


enum class Command {
    CHECK,
    WRITE,
    WRITE_AND_CHECK,
    NONE
}