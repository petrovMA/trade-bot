package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.*
import bot.trade.libs.m
import bot.trade.libs.round
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*


class TrendCalculator(
    client: Client,
    pair: TradePair,
    private val hma1: Pair<Duration, Int>,
    private val hma2: Pair<Duration, Int>,
    private val hma3: Pair<Duration, Int>,
    private val rsi1: Pair<Duration, Int>,
    private val rsi2: Pair<Duration, Int>,
    private val inputKlineInterval: Pair<Duration, INTERVAL> = 5.m() to INTERVAL.FIVE_MINUTES,
    endTime: Long? = System.currentTimeMillis()
) {
    private val log = KotlinLogging.logger {}

    private val objectMapper: ObjectMapper = ObjectMapper()

    private val hma1Converter = KlineConverter(inputKlineInterval.first, hma1.first, (hma1.second * 1.5).toInt())
    private val hma2Converter = KlineConverter(inputKlineInterval.first, hma2.first, (hma2.second * 1.3).toInt())
    private val hma3Converter = KlineConverter(inputKlineInterval.first, hma3.first, (hma3.second * 1.2).toInt())
    private val rsi1Converter = KlineConverter(inputKlineInterval.first, rsi1.first, (rsi1.second + 1))
    private val rsi2Converter = KlineConverter(inputKlineInterval.first, rsi2.first, (rsi2.second + 1))

    init {
        initKlineForIndicator(client, pair, setOf(hma1, hma2, hma3, rsi1, rsi2), endTime ?: System.currentTimeMillis())
    }

    /**
     * @param candlesticks - small candlesticks !! put here only closed candlesticks
     */
    fun addCandlesticks(vararg candlesticks: Candlestick) {
        hma1Converter.addCandlesticks(*candlesticks)
        hma2Converter.addCandlesticks(*candlesticks)
        hma3Converter.addCandlesticks(*candlesticks)
        rsi1Converter.addCandlesticks(*candlesticks)
        rsi2Converter.addCandlesticks(*candlesticks)
    }

    fun getTrend(): Trend = Trend(
        hma1 = calcHMA(hma1Converter.getCandlesticks(), hma1.second),
        hma2 = calcHMA(hma2Converter.getCandlesticks(), hma2.second),
        hma3 = calcHMA(hma3Converter.getCandlesticks(), hma3.second),
        rsi1 = calcRSI(rsi1Converter.getCandlesticks(), rsi1.second),
        rsi2 = calcRSI(rsi2Converter.getCandlesticks(), rsi2.second)
    )

    private fun initKlineForIndicator(
        client: Client,
        pair: TradePair,
        klineConverterParams: Set<Pair<Duration, Int>>,
        endIndicatorTime: Long
    ) {

        val milliseconds = klineConverterParams.maxOfOrNull { it.first.toMillis() * it.second }!!

        val endTime = endIndicatorTime.let { it - it % inputKlineInterval.first.toMillis() }
        var startTime = endTime - milliseconds

        do {
            client.getCandlestickBars(
                pair = pair,
                interval = inputKlineInterval.second,
                countCandles = 1000,
                start = startTime,
                end = null
            )
                .also { startTime = it.first().closeTime }
                .filter { it.closeTime < endTime }
                .toTypedArray()
                .also {
                    hma1Converter.addCandlesticks(*it)
                    hma2Converter.addCandlesticks(*it)
                    hma3Converter.addCandlesticks(*it)
                    rsi1Converter.addCandlesticks(*it)
                    rsi2Converter.addCandlesticks(*it)
                }

        } while (startTime < endTime)
    }

    private fun calcHMA(kline: List<Candlestick>, period: Int): BigDecimal {

        val client = OkHttpClient()

        val dataList = kline.map { it.close }.reversed()

        val params = TreeMap<String, Any>().apply {
            put("data_list", dataList)
            put("hma_period", period)
        }

        val request = Request.Builder().url("http://95.217.0.250:5000/hma").post(body(params)).build()

        val respBody = client.newCall(request).execute().body!!.string()

        val time = Instant.ofEpochMilli(kline.first().closeTime).atOffset(ZoneOffset.UTC).toString()

        val result = objectMapper.readTree(respBody)["hma"].decimalValue().round(2)

        log.info("HMA_$period for time $time: $result")

        return result
    }

    private fun calcRSI(kline: List<Candlestick>, period: Int): BigDecimal {

        val client = OkHttpClient()

        val dataList = kline.map { it.close }.reversed()

        val params = TreeMap<String, Any>().apply {
            put("data_list", dataList)
            put("rsi_period", period)
        }

        val request = Request.Builder().url("http://95.217.0.250:5000/rsi").post(body(params)).build()

        val respBody = client.newCall(request).execute().body!!.string()

        val time = Instant.ofEpochMilli(kline.first().closeTime).atOffset(ZoneOffset.UTC).toString()

        val result = objectMapper.readTree(respBody)["rsi"].decimalValue()

        log.info("RSI_$period for time $time: $result")

        return result
    }

    private fun body(obj: Any) =
        objectMapper.writeValueAsString(obj).toRequestBody("application/json".toMediaTypeOrNull()!!)

    data class Trend(
        val hma1: BigDecimal? = null,
        val hma2: BigDecimal? = null,
        val hma3: BigDecimal? = null,
        val rsi1: BigDecimal? = null,
        val rsi2: BigDecimal? = null
    )

    fun rsiTradingView(closingPrices: List<Double>, period: Int = 14, roundRsi: Boolean = true): List<Double> {
        if (closingPrices.size <= period) {
            throw IllegalArgumentException("Not enough data to calculate RSI")
        }

        val delta = closingPrices.zipWithNext { a, b -> b - a }
        val up = delta.map { if (it > 0) it else 0.0 }
        val down = delta.map { if (it < 0) -it else 0.0 }

        val alpha = 1.0 / period
        var avgUp = up.takeLast(period).average()
        var avgDown = down.takeLast(period).average()

        val rsi = mutableListOf<Double>()

        for (i in 0 until period) {
            avgUp = (avgUp * (1 - alpha)) + (up[i] * alpha)
            avgDown = (avgDown * (1 - alpha)) + (down[i] * alpha)

            val rs = if (avgDown == 0.0) Double.MAX_VALUE else avgUp / avgDown
            val rsiValue = 100 - (100 / (1 + rs))

            rsi.add(if (roundRsi) "%.2f".format(rsiValue).replace(",", ".").toDouble() else rsiValue)
        }

        return rsi
    }
}