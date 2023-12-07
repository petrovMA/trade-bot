package bot.trade.exchanges

import bot.trade.libs.ListLimit
import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
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

fun getConfigByExchange(enum: ExchangeEnum) = readConf("exchangeConfigs/$enum.conf")