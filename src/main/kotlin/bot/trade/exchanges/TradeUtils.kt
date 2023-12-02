package bot.trade.exchanges

import bot.trade.libs.ListLimit
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.libs.KlineConverter
import bot.trade.libs.m
import bot.trade.libs.readConf
import mu.KLogger
import java.math.BigDecimal
import java.time.Duration


fun initKlineForIndicator(client: ClientByBit, pair: TradePair, outputKlineInterval: Duration /* todo Add functionality: one call `getCandlestickBars` for generate several KlineConverters */, klineAmount: Int): KlineConverter {
    val inputKlineInterval = 5.m() to INTERVAL.FIVE_MINUTES

    val milliseconds = outputKlineInterval.toMillis() * klineAmount

    val klineConverter = KlineConverter(inputKlineInterval.first, outputKlineInterval, klineAmount)

    val endTime = System.currentTimeMillis().let { it - it % inputKlineInterval.first.toMillis() }
    var startTime = endTime - milliseconds

    do {
        val fiveMinutes = client.getCandlestickBars(
            pair = pair,
            interval = inputKlineInterval.second,
            countCandles = 1000,
            start = startTime,
            end = null
        )

        klineConverter.addCandlesticks(*fiveMinutes.toTypedArray())
        startTime = fiveMinutes.first().closeTime

    } while (startTime < endTime)

    return klineConverter
}

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