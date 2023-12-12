package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.Candlestick
import bot.trade.libs.h
import bot.trade.libs.m
import bot.trade.libs.toArrayList
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import utils.mapper.Mapper.asListObjects
import utils.resourceFile
import java.math.BigDecimal

class KlineConverterTest {

    @Test
    fun checkKlineConverter() {
        val type = object : TypeToken<List<Candlestick>>() {}.type
        val expected = asListObjects<Candlestick>(resourceFile<KlineConverterTest>("expected.json").readText(), type).reversed()
        val input = asListObjects<Candlestick>(resourceFile<KlineConverterTest>("input.json").readText(), type)

        val klineConverter = KlineConverter(5.m(), 2.h(), 20)
        klineConverter.addCandlesticks(*input.toTypedArray())

        assertEquals(expected.take(7), klineConverter.getCandlesticks())
        klineConverter.closeCurrentCandlestick()
        assertEquals(expected, klineConverter.getCandlesticks())
    }

    @Test
    fun checkWrongKlineException() {
        val type = object : TypeToken<List<Candlestick>>() {}.type
        val input = asListObjects<Candlestick>(resourceFile<KlineConverterTest>("input.json").readText(), type)
            .toArrayList()
            .apply {
                add(
                    10, Candlestick(
                        openTime = 1701410400000,
                        closeTime = 1701411000000,
                        open = BigDecimal(2092.65),
                        high = BigDecimal(2093.66),
                        low = BigDecimal(2090.8),
                        close = BigDecimal(2090.97),
                        volume = BigDecimal(2505.36)
                    )
                )
            }

        val klineConverter = KlineConverter(5.m(), 2.h(), 20)

        assertThrows(Exception::class.java) { klineConverter.addCandlesticks(*input.toTypedArray()) }
    }

    @Test
    fun checkKlineNoSequenceException() {
        val type = object : TypeToken<List<Candlestick>>() {}.type
        val input = asListObjects<Candlestick>(resourceFile<KlineConverterTest>("input.json").readText(), type)
            .toArrayList()
            .apply { removeAt(10) }

        val klineConverter = KlineConverter(5.m(), 2.h(), 20)

        assertThrows(Exception::class.java) { klineConverter.addCandlesticks(*input.toTypedArray()) }
    }
}