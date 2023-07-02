package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.libs.readConf
import mu.KLogger
import java.math.BigDecimal


fun calcAveragePriceStatic(
    currentCandlestickList: ListLimit<Candlestick>,
    countCandles: Int,
    intervalCandlesBuy: Int,
    intervalCandlesSell: Int,
    symbols: TradePair,
    interval: INTERVAL,
    client: Client,
    log: KLogger?,
    isEmulate: Boolean
): Triple<BigDecimal, BigDecimal, ListLimit<Candlestick>> {


    var countHigh = 0
    var countLow = 0
    var candlestickList = currentCandlestickList
    var averageHigh = 0.toBigDecimal()
    var averageLow = 0.toBigDecimal()

    if (candlestickList.isEmpty()) {
        candlestickList = ListLimit(countCandles)

        client.getCandlestickBars(symbols, interval, countCandles + 1)
            .forEach { candlestickList.add(it) }

        log?.info("$symbols Init candlestickList:\n${candlestickList}")
    }

    if (!isEmulate)
        for (i in 0 until candlestickList.size - 1) {
            if (candlestickList[i].closeTime + 1 != candlestickList[i + 1].openTime) {
                log?.warn("$symbols candlestickList not sequence:\n${candlestickList[i]}\n${candlestickList[i + 1]}")
                candlestickList = ListLimit(countCandles)

                client.getCandlestickBars(symbols, interval, countCandles + 1)
                    .forEach { candlestickList.add(it) }

                break
            }
        }

    for ((i, event) in candlestickList.withIndex()) {
        if (countCandles - intervalCandlesBuy <= i) {
            averageHigh += event.high
            countHigh++
        }
        if (countCandles - intervalCandlesSell <= i) {
            averageLow += event.low
            countLow++
        }
    }

    averageHigh /= BigDecimal(countHigh)
    averageLow /= BigDecimal(countLow)
    log?.debug("$symbols averageHigh $averageHigh || averageLow $averageLow")
    return Triple(averageHigh, averageLow, candlestickList)
}


class KlineConstructor(val interval: INTERVAL) {
    private val millsInterval = interval.toMillsTime()
    private var lastCandlestick: LastCandlestick = LastCandlestick(
        candlestick = Candlestick(0, 0, BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0)),
        isClosed = true
    )

    fun nextKline(trade: Trade): List<LastCandlestick> {

        return when {
            lastCandlestick.isClosed -> {

                val openTime = trade.time - (trade.time % millsInterval)

                lastCandlestick = LastCandlestick(
                    isClosed = false,
                    candlestick = Candlestick(
                        openTime = openTime,
                        closeTime = openTime + millsInterval - 1,
                        open = trade.price,
                        close = trade.price,
                        high = trade.price,
                        low = trade.price,
                        volume = trade.qty
                    )
                )

                listOf(lastCandlestick)
            }

            lastCandlestick.candlestick.closeTime < trade.time -> {

                val openTime = trade.time - (trade.time % millsInterval)

                val cndls: ArrayList<LastCandlestick> = ArrayList()

                lastCandlestick = LastCandlestick(
                    isClosed = true,
                    candlestick = Candlestick(
                        openTime = lastCandlestick.candlestick.openTime,
                        closeTime = lastCandlestick.candlestick.closeTime,
                        open = lastCandlestick.candlestick.open,
                        close = lastCandlestick.candlestick.close,
                        high = lastCandlestick.candlestick.high,
                        low = lastCandlestick.candlestick.low,
                        volume = lastCandlestick.candlestick.volume
                    )
                )

                cndls.add(lastCandlestick)

                while (cndls.last().candlestick.closeTime + millsInterval < openTime) {
                    cndls.add(
                        LastCandlestick(
                            isClosed = true,
                            candlestick = Candlestick(
                                openTime = cndls.last().candlestick.openTime + millsInterval,
                                closeTime = cndls.last().candlestick.closeTime + millsInterval,
                                open = cndls.last().candlestick.open,
                                close = cndls.last().candlestick.close,
                                high = cndls.last().candlestick.high,
                                low = cndls.last().candlestick.low,
                                volume = BigDecimal(0)
                            )
                        )
                    )
                }

                lastCandlestick = LastCandlestick(
                    isClosed = false,
                    candlestick = Candlestick(
                        openTime = openTime,
                        closeTime = openTime + millsInterval - 1,
                        open = trade.price,
                        close = trade.price,
                        high = trade.price,
                        low = trade.price,
                        volume = trade.qty
                    )
                )

                cndls.add(lastCandlestick)

                cndls
            }

            else -> {
                val high =
                    if (trade.price > lastCandlestick.candlestick.high) trade.price else lastCandlestick.candlestick.high
                val low =
                    if (trade.price < lastCandlestick.candlestick.low) trade.price else lastCandlestick.candlestick.low

                lastCandlestick = LastCandlestick(
                    isClosed = false,
                    candlestick = Candlestick(
                        openTime = lastCandlestick.candlestick.openTime,
                        closeTime = lastCandlestick.candlestick.closeTime,
                        open = lastCandlestick.candlestick.open,
                        close = trade.price,
                        high = high,
                        low = low,
                        volume = lastCandlestick.candlestick.volume + trade.qty
                    )
                )

                listOf(lastCandlestick)
            }
        }
    }

    data class LastCandlestick(val isClosed: Boolean, val candlestick: Candlestick)
    fun getCandlestick() = lastCandlestick.candlestick
}

fun getConfigByExchange(enum: ExchangeEnum) = readConf("exchangeConfigs/$enum.conf")