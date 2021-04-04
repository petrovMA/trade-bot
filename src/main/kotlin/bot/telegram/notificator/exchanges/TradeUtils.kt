package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.exchanges.clients.*
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
        candlestickList.addAll(client.getCandlestickBars(symbols, interval, countCandles + 1)
            .run { subList(0, if (size - 1 < countCandles) size - 1 else countCandles) })
        log?.info("$symbols Init candlestickList:\n${candlestickList}")
    }

    if (!isEmulate)
        for (i in 0 until candlestickList.size - 1) {
            if (candlestickList[i].closeTime + 1 != candlestickList[i + 1].openTime) {
                log?.warn("$symbols candlestickList not sequence:\n$candlestickList[i]\n${candlestickList[i + 1]}")
                candlestickList = ListLimit(countCandles)
                candlestickList.addAll(client.getCandlestickBars(symbols, interval, countCandles + 1)
                    .run { subList(0, if (size - 1 < countCandles) size - 1 else countCandles) })
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
    private var candlestick = true to Candlestick(0, 0, BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0))

    fun nextKline(trade: Trade): List<Pair<Boolean, Candlestick>> {

        return when {
            candlestick.first -> {

                val openTime = trade.time - (trade.time % millsInterval)

                candlestick = false to Candlestick(
                    openTime = openTime,
                    closeTime = openTime + millsInterval - 1,
                    open = trade.price,
                    close = trade.price,
                    high = trade.price,
                    low = trade.price,
                    volume = trade.qty
                )

                listOf(candlestick)
            }
            candlestick.second.closeTime < trade.time -> {

                val openTime = trade.time - (trade.time % millsInterval)

                val cndls: ArrayList<Pair<Boolean, Candlestick>> = ArrayList()

                candlestick = true to Candlestick(
                    openTime = candlestick.second.openTime,
                    closeTime = candlestick.second.closeTime,
                    open = candlestick.second.open,
                    close = candlestick.second.close,
                    high = candlestick.second.high,
                    low = candlestick.second.low,
                    volume = candlestick.second.volume
                )

                cndls.add(candlestick)

                while (cndls.last().second.closeTime + millsInterval < openTime) {
                    cndls.add(
                        true to Candlestick(
                            openTime = cndls.last().second.openTime + millsInterval,
                            closeTime = cndls.last().second.closeTime + millsInterval,
                            open = cndls.last().second.open,
                            close = cndls.last().second.close,
                            high = cndls.last().second.high,
                            low = cndls.last().second.low,
                            volume = BigDecimal(0)
                        )
                    )
                }

                candlestick = false to Candlestick(
                    openTime = openTime,
                    closeTime = openTime + millsInterval - 1,
                    open = trade.price,
                    close = trade.price,
                    high = trade.price,
                    low = trade.price,
                    volume = trade.qty
                )

                cndls.add(candlestick)

                cndls
            }
            else -> {
                val high = if (trade.price > candlestick.second.high) trade.price else candlestick.second.high
                val low = if (trade.price < candlestick.second.low) trade.price else candlestick.second.low
                candlestick = false to Candlestick(
                    openTime = candlestick.second.openTime,
                    closeTime = candlestick.second.closeTime,
                    open = candlestick.second.open,
                    close = trade.price,
                    high = high,
                    low = low,
                    volume = candlestick.second.volume + trade.qty
                )

                listOf(candlestick)
            }
        }
    }
}