package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.Candlestick
import bot.trade.exchanges.clients.ClientByBit
import bot.trade.exchanges.clients.INTERVAL
import bot.trade.exchanges.clients.TradePair
import com.google.gson.reflect.TypeToken
import utils.mapper.Mapper
import utils.resourceFile

class ClientByBitStub : ClientByBit("", "") {
    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> = Mapper.asListObjects(
        resourceFile<KlineConverterTest>("trend_calc_input.json").readText(),
        object : TypeToken<List<Candlestick>>() {}.type
    )
}