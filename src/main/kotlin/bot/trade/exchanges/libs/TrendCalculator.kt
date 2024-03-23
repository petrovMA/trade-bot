package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.*
import bot.trade.libs.m
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ta4j.core.Bar
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.indicators.HMAIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import java.math.BigDecimal
import java.time.Duration


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

    private var trend: Trend? = null

    private val objectMapper: ObjectMapper = ObjectMapper()

    private var isTrendUpdated = true

    val hma1Converter = KlineConverter(inputKlineInterval.first, hma1.first, (hma1.second * 1.5).toInt())
    val hma2Converter = KlineConverter(inputKlineInterval.first, hma2.first, (hma2.second * 1.3).toInt())
    val hma3Converter = KlineConverter(inputKlineInterval.first, hma3.first, (hma3.second * 1.2).toInt())
    val rsi1Converter = KlineConverter(inputKlineInterval.first, rsi1.first, (rsi1.second + 1))
    val rsi2Converter = KlineConverter(inputKlineInterval.first, rsi2.first, (rsi2.second + 1))

    init {
        initKlineForIndicator(
            client = client,
            pair = pair,
            klineConverterParams = listOf(
                hma1.first to (hma1.second * 1.5).toInt(),
                hma2.first to (hma2.second * 1.3).toInt(),
                hma3.first to (hma3.second * 1.2).toInt(),
                rsi1.first to (rsi1.second + 1),
                rsi2.first to (rsi2.second + 1)
            ),
            endIndicatorTime = endTime ?: System.currentTimeMillis()
        )
    }

    fun addCandlesticks(vararg candlesticks: Candlestick) {
        val isHma1 = hma1Converter.addCandlesticks(*candlesticks)
        val isHma2 = hma2Converter.addCandlesticks(*candlesticks)
        val isHma3 = hma3Converter.addCandlesticks(*candlesticks)
        val isRsi1 = rsi1Converter.addCandlesticks(*candlesticks)
        val isRsi2 = rsi2Converter.addCandlesticks(*candlesticks)

        isTrendUpdated = (isTrendUpdated || isHma1 || isHma2 || isHma3 || isRsi1 || isRsi2)
    }

    private fun calcTrend() {
        val hma1 = ta4jHma(hma1Converter.getBars(), hma1.second)
        val hma2 = ta4jHma(hma2Converter.getBars(), hma2.second)
        val hma3 = ta4jHma(hma3Converter.getBars(), hma3.second)
        val rsi1 = ta4jRsi(rsi1Converter.getBars(), rsi1.second)
        val rsi2 = ta4jRsi(rsi2Converter.getBars(), rsi2.second)
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

        this.trend = Trend(
            hma1 = hma1,
            hma2 = hma2,
            hma3 = hma3,
            rsi1 = rsi1,
            rsi2 = rsi2,
            trend = trend
        )
    }

    fun getTrend(): Trend? {
        if (isTrendUpdated) {
            calcTrend()
            isTrendUpdated = false
        }
        return trend
    }

    private fun initKlineForIndicator(
        client: Client,
        pair: TradePair,
        klineConverterParams: List<Pair<Duration, Int>>,
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
                .sortedBy { it.closeTime }
                .also { startTime = it.last().closeTime }
                .forEach {
                    hma1Converter.addCandlesticks(it)
                    hma2Converter.addCandlesticks(it)
                    hma3Converter.addCandlesticks(it)
                    rsi1Converter.addCandlesticks(it)
                    rsi2Converter.addCandlesticks(it)

                    if (endTime < it.closeTime)
                        return
                }

        } while (true)
    }

    private fun ta4jHma(kline: List<Bar>, hmaPeriod: Int): BigDecimal =
        HMAIndicator(ClosePriceIndicator(BaseBarSeries(kline)), hmaPeriod)
            .getValue(kline.size - 1).doubleValue().toBigDecimal()

    private fun ta4jRsi(kline: List<Bar>, rsiPeriod: Int): BigDecimal =
        RSIIndicator(ClosePriceIndicator(BaseBarSeries(kline)), rsiPeriod)
            .getValue(kline.size - 1).doubleValue().toBigDecimal()

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
}