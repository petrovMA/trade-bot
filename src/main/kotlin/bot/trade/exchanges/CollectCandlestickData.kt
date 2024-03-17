package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import bot.trade.libs.*
import mu.KLogger
import mu.KotlinLogging
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.*


fun main() {

    CollectCandlestickData(
        command = Command.WRITE,
//            command = Command.WRITE,
//            command = Command.CHECK,
        exchangeEnum = ExchangeEnum.BINANCE
    ) { _, _ -> }.run()


//        .fromTextToDataBase(
//            "/home/asus/Blockchain/CandlestickData/BITMAX.db",
//            "/home/asus/Blockchain/CandlestickData/BITMAX/BTT_BTC/2021_01"
//        )

}

class CollectCandlestickData(
    private val command: Command,
    firstDay: LocalDate? = null,
    private val exchangeEnum: ExchangeEnum,
    val sendMessage: (String, Boolean) -> Unit
) : Thread() {
    private val maxLimit = 1000
    private val log = KotlinLogging.logger {}

    private val properties = try {
        when (exchangeEnum) {
            ExchangeEnum.BINANCE -> readConf("collect_binance_candlestick.conf")
                ?: throw RuntimeException("Can't read Config File!")
            ExchangeEnum.BITMAX -> readConf("collect_bitmax_candlestick.conf")
                ?: throw RuntimeException("Can't read Config File!")
            ExchangeEnum.HUOBI -> readConf("collect_huobi_candlestick.conf")
                ?: throw RuntimeException("Can't read Config File!")
            ExchangeEnum.GATE -> readConf("collect_gate_candlestick.conf")
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
            ExchangeEnum.BINANCE -> ClientBinance()
            ExchangeEnum.BITMAX -> ClientBitmax()
            ExchangeEnum.HUOBI -> ClientHuobi()
            ExchangeEnum.GATE -> ClientGate()
            else -> throw UnsupportedExchangeException()
        }
        try {
            when (command) {
                Command.NONE -> Unit
                Command.CHECK -> {
                    checkCandlesticks(pathDB)
                    send("#CollectCandlestickData #$exchangeEnum check from date $firstDay done")
                }
                Command.WRITE -> {
                    writeCandlesToDB(pathDB, client)
                    send("#CollectCandlestickData #$exchangeEnum write done")
                }
                Command.WRITE_AND_CHECK -> {
                    writeCandlesToDB(pathDB, client)
                    send("#CollectCandlestickData #$exchangeEnum write done, starts check from date $firstDay")
                    checkCandlesticks(pathDB)
                    send("#CollectCandlestickData #$exchangeEnum check from date $firstDay done")
                }
                Command.CUSTOM -> {
                    deleteAllEmpty(pathDB, client)
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Error in threads.", t)
            send("#CollectCandlestickData #$exchangeEnum error: \n${printTrace(t)}")
        }
    }

    private fun checkCandlesticks(path: String) {

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
                    send("#CollectCandlestickData #$exchangeEnum: errors\n$errMsg")
                    errMsg = ""
                }

            }

            statementCandles.close()
        }
        stmt.close()
        connect.close()

        if (errMsg.isNotBlank())
            send("#CollectCandlestickData #$exchangeEnum: errors\n$errMsg")
    }


    private fun writeCandlesToDB(pathDB: String, client: Client) {
        var pairCounter = 0
        client
            .getAllPairs()
            .minus(ignorePairs)
            .run {
                forEach { tradePair ->

                    log.debug {
                        val s = "Write pair: $size/${++pairCounter}"
                        "$s${".".repeat(24 - s.length)}$tradePair"
                    }

                    try {
                        writeCandlesDB(
                            candlesticks = client.getCandlestickBars(tradePair, INTERVAL.FIVE_MINUTES, maxLimit)
                                .filter { it.volume > BigDecimal(0) },
                            pair = tradePair,
                            pathDB = pathDB
                        )
                    } catch (t: Throwable) {
                        send("Can't write pair: $tradePair Error:\n${printTrace(t)}")
                        log.warn("Can't write pair: $tradePair Error:", t)
                    }
                }
            }
    }


    private fun deleteAllEmpty(pathDB: String, client: Client) {

        val connect = connect(pathDB, log)
        var pairCounter = 0

        client
            .getAllPairs()
            .run {
                forEach { tradePair ->
                    pairCounter++

                    try {

                        val iterator = CandlestickListsIterator(
                            connect,
                            tableName = "CANDLESTICK_$tradePair",
                            listSize = 500,
                            startDateTime = Timestamp.valueOf("2017-10-02 00:00:00.000000"),
                            endDateTime = Timestamp.valueOf("2022-10-02 00:00:00.000000"),
                            interval = INTERVAL.FIVE_MINUTES,
                            fillGaps = false
                        )

                        val stmt = connect.createStatement()
                        var counter = 0

                        iterator.forEach { candles ->
                            candles.forEach {
                                if (it.volume <= BigDecimal.ZERO) {
                                    stmt.executeUpdate("""DELETE FROM CANDLESTICK_$tradePair WHERE ID_OPEN_TIME = ${it.openTime};""")
                                    counter++
                                }
                            }
                        }

                        log.info {
                            val s1 = "for $tradePair"
                            val s2 = "$counter removed!"
                            s1 + ".".repeat(20 - s1.length) + s2 + ".".repeat(15 - s2.length) + "....$size/$pairCounter"
                        }

                    } catch (t: Throwable) {
                        send("Can't write pair: $tradePair Error:\n${printTrace(t)}")
                        log.warn("Can't write pair: $tradePair Error:", t)
                    }
                }
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
        } catch (e: Exception) {
            log.info("Can't find table: $tableName")
        } catch (t: Throwable) {
            log.info("Error:\n", t)
        } finally {
            stmt.close()
        }
        return lastCandlestick
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

    private fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)
}


class CandlestickListsIterator(
    connect: Connection,
    private val tableName: String,
    private val listSize: Int,
    startDateTime: Timestamp,
    endDateTime: Timestamp,
    private val fillGaps: Boolean,
    private val interval: INTERVAL
) : Iterator<List<Candlestick>> {

    private val stmt = connect.createStatement()
    private val resultSet: ResultSet = stmt.executeQuery(
        """SELECT * FROM $tableName WHERE 
          ID_OPEN_TIME > ${startDateTime.time} AND
          ID_OPEN_TIME < ${endDateTime.time} ORDER BY ID_OPEN_TIME"""
    )
    private val log = KotlinLogging.logger {}
    private var hasNext = true
    private var current: Candlestick? = null
    private var previous: Candlestick? = null
    private var next = true

    override fun hasNext(): Boolean = hasNext

    override fun next(): List<Candlestick> {
        val resultList = ArrayList<Candlestick>()
        try {

            if (fillGaps && previous?.let { it.closeTime + 1 != current!!.openTime } == true) {
                next = resultSet.next()
                current = getCandle(resultSet).toCandlestick()
            }

            while (resultList.size < listSize) {

                if (fillGaps && previous?.let { it.closeTime + 1 != current!!.openTime } == true) {
                    previous = nextEmptyCandlestick(previous!!, interval)
                    resultList.add(previous!!)

                } else {
                    current?.let { resultList.add(it) }

                    if (resultSet.next().apply { next = this }.not()) break
                    else {
                        previous = current
                        current = getCandle(resultSet).toCandlestick()
                    }
                }
            }

            if (!next) {
                stmt.close()
                hasNext = false
            }

        } catch (t: Throwable) {
            log.error("Table: $tableName can't read!", t)
            throw t
        }

        return resultList
    }


    private fun nextEmptyCandlestick(previous: Candlestick, interval: INTERVAL): Candlestick = Candlestick(
        openTime = previous.openTime + interval.toMillsTime(),
        closeTime = previous.closeTime + interval.toMillsTime(),
        open = previous.close,
        close = previous.close,
        high = previous.close,
        low = previous.close,
        volume = BigDecimal.ZERO
    )
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
    log.error("Connect Error:", t)
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
    CUSTOM,
    NONE
}