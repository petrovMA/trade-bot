package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.Candlestick
import bot.trade.exchanges.clients.TradePair
import bot.trade.libs.h
import bot.trade.libs.m
import bot.trade.libs.round
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.mapper.Mapper
import utils.resourceFile
import java.math.BigDecimal


class TrendCalculatorTest {

    // @Test // TODO:: this test works only with server: http://95.217.0.250:5000/
    fun getTrend() {
        val trendCalculator = TrendCalculator(
            ClientByBitStub(),
            TradePair("ETH_USDT"),
            30.m() to 10,
            15.m() to 70,
            5.m() to 200,
            2.h() to 14,
            4.h() to 14,
            endTime = 1701849900000
        )

        Mapper.asListObjects<Candlestick>(
            resourceFile<KlineConverterTest>("trend_calc_input_web_socket.json").readText(),
            object : TypeToken<List<Candlestick>>() {}.type
        )
            .sortedBy { it.openTime }
            .forEach { trendCalculator.addCandlesticks(it) }

        trendCalculator.getTrend().run {
            assertEquals(BigDecimal(2267.94).round(2), hma1)
            assertEquals(BigDecimal(2275.61).round(2), hma2)
            assertEquals(BigDecimal(2271.70).round(2), hma3)
            assertEquals(BigDecimal(56.56).round(2), rsi1)
            assertEquals(BigDecimal(61.12).round(2), rsi2)
        }
    }
}