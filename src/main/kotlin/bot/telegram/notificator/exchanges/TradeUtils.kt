package bot.telegram.notificator.exchanges

import bot.telegram.notificator.ListLimit
import bot.telegram.notificator.exchanges.clients.Candlestick
import bot.telegram.notificator.exchanges.clients.Client
import bot.telegram.notificator.exchanges.clients.INTERVAL
import bot.telegram.notificator.exchanges.clients.TradePair
import mu.KotlinLogging


private val log = KotlinLogging.logger {}

fun calcAveragePriceStatic(
    currentCandlestickList: ListLimit<Candlestick>,
    countCandles: Int,
    intervalCandlesBuy: Int,
    intervalCandlesSell: Int,
    symbols: TradePair,
    interval: INTERVAL,
    client: Client,
): Triple<Double, Double, ListLimit<Candlestick>> {


    var countHigh = 0
    var countLow = 0
    var candlestickList = currentCandlestickList
    var averageHigh = 0.0
    var averageLow = 0.0

    if (candlestickList.isEmpty()) {
        candlestickList = ListLimit(countCandles)
        candlestickList.addAll(client.getCandlestickBars(symbols, interval, countCandles + 1)
                .run { subList(0, if (size - 1 < countCandles) size - 1 else countCandles) })
        log.info("$symbols Init candlestickList:\n${candlestickList}")
    }

    for ((i, event) in candlestickList.withIndex()) {
        if (i < candlestickList.size - 1)
            if (event.closeTime + 1 != candlestickList[i + 1].openTime) {
                log.warn("$symbols candlestickList not sequence, elements:\n$event\n${candlestickList[i + 1]}")
                candlestickList = ListLimit(countCandles)
                candlestickList.addAll(client.getCandlestickBars(symbols, interval, countCandles + 1)
                        .run { subList(0, if (size - 1 < countCandles) size - 1 else countCandles) })
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

    averageHigh /= countHigh
    averageLow /= countLow
    log.debug("$symbols averageHigh $averageHigh || averageLow $averageLow")
    return Triple(averageHigh, averageLow, candlestickList)
}