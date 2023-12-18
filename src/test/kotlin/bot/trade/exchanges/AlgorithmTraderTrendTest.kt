package bot.trade.exchanges

import bot.trade.exchanges.clients.Candlestick
import org.junit.jupiter.api.Test
import utils.mapper.Mapper.asObject

class AlgorithmTraderTrendTest {

    @Test
    fun test() {
        // last kline time: GMT: Sunday, 17 December 2023 г., 5:00:00
        // hma1 = 0.5099, hma2 = 0.5059, hma3 = 0.5038 rsi30m = 62.4481 rsi1h = 63.5574 rsi2h = 56.876

        val candlestickData = "klines_data.txt".file().readLines().map { asObject<Candlestick>(it) }
        val expectedCandlestick = "expected_klines.txt".file().readLines().map { asObject<Candlestick>(it) }
        val streamData = "klines_stream.txt".file().readLines().map { asObject<Candlestick>(it) }

        val (algorithmTrader, exchange) = testExchange("testExecuteTrendSettings.json", candlestickData.last().openTime)

        exchange.addKlineData(candlestickData)

        algorithmTrader.setup()

        streamData.forEach { algorithmTrader.handle(it) }

        algorithmTrader
    }
}