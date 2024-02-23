package bot.trade.exchanges

import bot.trade.exchanges.clients.Candlestick
import bot.trade.exchanges.libs.TrendCalculator
import org.junit.jupiter.api.Test
import utils.mapper.Mapper.asObject
import java.math.BigDecimal


class AlgorithmTraderTrendTest {

    //@Test // TODO:: this test works only with server: http://95.217.0.250:5000/
    fun test() {
        // last kline time: GMT: Sunday, 17 December 2023 г., 5:00:00
        // hma1 = 0.5099, hma2 = 0.5059, hma3 = 0.5038 rsi30m = 62.4481 rsi1h = 63.5574 rsi2h = 56.876

        // Trend(hma1=0.51, hma2=0.51, hma3=0.50, rsi1=68.13, rsi2=64.84, trend=LONG)
        // Trend(hma1=0.51, hma2=0.51, hma3=0.50, rsi1=68.93, rsi2=65.59, trend=LONG)

        val candlestickData = "klines_data.txt".file().readLines()
            .map { asObject<Candlestick>(it) }
            .subList(0, 300)

        val expectedCandlestick = "expected_klines.txt".file().readLines().map { asObject<Candlestick>(it) }
        val streamData = "klines_stream.txt".file().readLines().map { asObject<Candlestick>(it) }

        val (algorithmTrader, exchange) = testExchange("testExecuteTrendSettings.json", candlestickData.last().openTime)

        exchange.addKlineData(candlestickData)

        algorithmTrader.setup()

        streamData.forEach { algorithmTrader.handle(it) }

        algorithmTrader.getTrend()!!.run {
            assertIndicator(BigDecimal(0.51), hma1, BigDecimal(0.1))
            assertIndicator(BigDecimal(0.51), hma2, BigDecimal(0.1))
            assertIndicator(BigDecimal(0.50), hma3, BigDecimal(0.1))
            assertIndicator(BigDecimal(68.13), rsi1, BigDecimal(0.1))
            assertIndicator(BigDecimal(64.84), rsi2, BigDecimal(0.1))
        }

        val field = AlgorithmTrader::class.java.getDeclaredField("trendCalculator")
        field.isAccessible = true

        val trendCalculator = field.get(algorithmTrader) as TrendCalculator

        assertCandlesticks(expectedCandlestick, trendCalculator.hma3Converter.getCandlesticks())
    }
}