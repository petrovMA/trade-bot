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
import java.lang.Thread.sleep
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

    fun getTrend(): Trend {
        val hma1 = calcHMA(hma1Converter.getCandlesticks(), hma1.second)
        val hma2 = calcHMA(hma2Converter.getCandlesticks(), hma2.second)
        val hma3 = calcHMA(hma3Converter.getCandlesticks(), hma3.second)
        val rsi1 = rsiTradingView(rsi1Converter.getCandlesticks(), rsi1.second)
        val rsi2 = rsiTradingView(rsi2Converter.getCandlesticks(), rsi2.second)
        val trend = if (rsi1 > BigDecimal(50)) {
            if (rsi2 > BigDecimal(50)) {
                if (hma1 < hma2 && hma2 < hma3) Trend.TREND.HEDGE
                else Trend.TREND.LONG
            } else Trend.TREND.FLAT
        } else {
            if (rsi2 < BigDecimal(50)) {
                if (hma1 > hma2 && hma2 > hma3) Trend.TREND.HEDGE
                else Trend.TREND.SHORT
            } else Trend.TREND.FLAT
        }

        log.info("Trend: $trend hma1=$hma1, hma2=$hma2, hma3=$hma3, rsi1=$rsi1, rsi2=$rsi2")

        return Trend(
            hma1 = hma1,
            hma2 = hma2,
            hma3 = hma3,
            rsi1 = rsi1,
            rsi2 = rsi2,
            trend = trend
        )
    }

    private fun initKlineForIndicator(
        client: Client,
        pair: TradePair,
        klineConverterParams: Set<Pair<Duration, Int>>,
        endIndicatorTime: Long
    ) {

        val milliseconds = klineConverterParams.maxOfOrNull { it.first.toMillis() * (it.second + 1) }!!

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
                .filter { it.closeTime < endIndicatorTime }
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

        val dataList = kline.map { it.close }

        val params = TreeMap<String, Any>().apply {
            put("data_list", dataList)
            put("hma_period", period)
        }

        val request = Request.Builder().url("http://95.217.0.250:5000/hma").post(body(params)).build()

        var resp: okhttp3.Response

        do {
            resp = try {
                client.newCall(request).execute()
            } catch (t: Throwable) {
                log.error("Error hma calculation request: ${t.message}")
                sleep(200)
                continue
            }
            if (resp.code != 200) {
                log.error("Error hma calculation response, code: ${resp.code}, bode: \n${resp.body!!.string()}")
                sleep(200)
            } else break
        } while (true)

        val time = Instant.ofEpochMilli(kline.first().closeTime).atOffset(ZoneOffset.UTC).toString()

        val result = objectMapper.readTree(resp.body!!.string())["hma"].decimalValue().round(2)

        log.info("HMA_$period for time $time: $result")

        return result
    }

    private fun calcRSI(kline: List<Candlestick>, period: Int): BigDecimal {

        val client = OkHttpClient()

        val dataList = kline.map { it.close }

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
        val hma1: BigDecimal,
        val hma2: BigDecimal,
        val hma3: BigDecimal,
        val rsi1: BigDecimal,
        val rsi2: BigDecimal,
        val trend: TREND
    ) {
        enum class TREND { LONG, SHORT, FLAT, HEDGE }
    }

    private fun rsiTradingView(kline: List<Candlestick>, period: Int = 14, roundRsi: Boolean = true): BigDecimal {

        val dataList = kline.map { it.close.toDouble() }

        if (dataList.size <= period) {
            throw IllegalArgumentException("Not enough data to calculate RSI")
        }

        val delta = dataList.zipWithNext { a, b -> b - a }
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

        return rsi.last().toBigDecimal()
    }
}