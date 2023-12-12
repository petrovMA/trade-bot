package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.Candlestick
import bot.trade.libs.ListLimit
import java.time.Duration

class KlineConverter(
    private val inputKlineInterval: Duration,
    private val outputKlineInterval: Duration,
    val size: Int = 201
) {

    init {
        if (outputKlineInterval.toMillis() % inputKlineInterval.toMillis() != 0L)
            throw Exception("inputKlineInterval % outputKlineInterval != 0")
    }

    private val candlesticks = ListLimit<Candlestick>(size)

    private var currentCandlestick: Candlestick? = null

    fun addCandlesticks(vararg inputCandlesticks: Candlestick) {
        inputCandlesticks
            .sortedBy { it.openTime }
            .forEach {
                if (
                    it.closeTime - it.openTime != inputKlineInterval.toMillis()
                    && it.closeTime + 1 - it.openTime != inputKlineInterval.toMillis()
                )
                    throw Exception("kline interval not equals to inputKlineInterval:\n${it}")

                if (currentCandlestick == null) {
                    currentCandlestick = Candlestick(
                        openTime = it.openTime - it.openTime % outputKlineInterval.toMillis(),
                        closeTime = it.closeTime,
                        open = it.open,
                        high = it.high,
                        low = it.low,
                        close = it.close,
                        volume = it.volume
                    )
                } else {
                    if (currentCandlestick!!.closeTime == it.openTime || currentCandlestick!!.closeTime + 1 == it.openTime)
                        currentCandlestick = Candlestick(
                            openTime = currentCandlestick!!.openTime,
                            closeTime = it.closeTime,
                            open = currentCandlestick!!.open,
                            high = if (it.high > currentCandlestick!!.high) it.high else currentCandlestick!!.high,
                            low = if (it.low < currentCandlestick!!.low) it.low else currentCandlestick!!.low,
                            close = it.close,
                            volume = currentCandlestick!!.volume + it.volume
                        )
                    else if (currentCandlestick!!.closeTime + 1 < it.openTime)
                        throw Exception("inputCandlesticks has a gap in sequence:\n${currentCandlestick}\n${it}")
                }

                if (
                    (currentCandlestick!!.closeTime + 1) % outputKlineInterval.toMillis() == 0L
                    || currentCandlestick!!.closeTime % outputKlineInterval.toMillis() == 0L
                )
                    closeCurrentCandlestick()
            }
    }

    fun closeCurrentCandlestick() {
        candlesticks.add(currentCandlestick!!)
        currentCandlestick = null
    }

    fun getCandlesticks(): List<Candlestick> = candlesticks
}