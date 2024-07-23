package bot.trade.exchanges

import bot.trade.database.repositories.ActiveOrdersRepository
import bot.trade.exchanges.clients.Candlestick
import bot.trade.exchanges.libs.TrendCalculator
import bot.trade.libs.round
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import utils.mapper.Mapper.asObject
import java.math.BigDecimal


@DataJpaTest
@ActiveProfiles("test")
class AlgorithmTraderTrendTest {

    @Autowired
    private lateinit var repository: ActiveOrdersRepository

    @Test
    fun test() {

        val candlestickData = "klines_data.txt".file().readLines()
            .map { asObject<Candlestick>(it) }
            .subList(0, 300)

        val expectedCandlestick = "expected_klines.txt".file().readLines().map { asObject<Candlestick>(it) }
        val streamData = "klines_stream.txt".file().readLines().map { asObject<Candlestick>(it) }

        val (algorithmTrader, exchange) = testExchange("testExecuteTrendSettings.json", repository, candlestickData.last().openTime)
            .run { first as AlgorithmTrader to second }

        exchange.addKlineData(candlestickData)

        algorithmTrader.setup()

        streamData.forEach { algorithmTrader.handle(it) }

        algorithmTrader.getTrend()!!.run {
            assertIndicator(BigDecimal(0.51), hma1, BigDecimal(0.01))
            assertIndicator(BigDecimal(0.51), hma2, BigDecimal(0.01))
            assertIndicator(BigDecimal(0.50), hma3, BigDecimal(0.01))
            assertIndicator(BigDecimal(68.93), rsi1.round(2), BigDecimal(0.01)) // 68.9286811692868
            assertIndicator(BigDecimal(65.59), rsi2.round(2), BigDecimal(0.01)) // 65.59467098685978
        }

        val field = AlgorithmTrader::class.java.getDeclaredField("trendCalculator")
        field.isAccessible = true

        val trendCalculator = field.get(algorithmTrader) as TrendCalculator

        assertCandlesticks(expectedCandlestick, trendCalculator.hma3Converter.getBars().map { Candlestick(it) })
    }
}