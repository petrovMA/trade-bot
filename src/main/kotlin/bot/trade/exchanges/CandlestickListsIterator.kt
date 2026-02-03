package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import mu.KotlinLogging
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp


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

