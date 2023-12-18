package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.Candlestick
import bot.trade.libs.ListLimit
import bot.trade.libs.round
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
    private var prevKline: Candlestick? = null

    fun addCandlesticks(vararg inputCandlesticks: Candlestick) {
        inputCandlesticks
            .sortedBy { it.openTime }
            .forEach {
                prevKline?.let { k ->
                    if (it.openTime >= k.closeTime) {
                        if (
                            k.closeTime - k.openTime != inputKlineInterval.toMillis()
                            && k.closeTime + 1 - k.openTime != inputKlineInterval.toMillis()
                        )
                            throw Exception("kline interval not equals to inputKlineInterval:\n${k}")

                        if (currentCandlestick == null) {
                            currentCandlestick = Candlestick(
                                openTime = k.openTime - k.openTime % outputKlineInterval.toMillis(),
                                closeTime = k.closeTime,
                                open = k.open,
                                high = k.high,
                                low = k.low,
                                close = k.close,
                                volume = k.volume
                            )
                        } else {
                            if (
                                currentCandlestick!!.closeTime == k.openTime
                                || currentCandlestick!!.closeTime + 1 == k.openTime
                            )
                                currentCandlestick = Candlestick(
                                    openTime = currentCandlestick!!.openTime,
                                    closeTime = k.closeTime,
                                    open = currentCandlestick!!.open,
                                    high = if (k.high > currentCandlestick!!.high) k.high else currentCandlestick!!.high,
                                    low = if (k.low < currentCandlestick!!.low) k.low else currentCandlestick!!.low,
                                    close = k.close,
                                    volume = currentCandlestick!!.volume + k.volume
                                )
                            else if (currentCandlestick!!.closeTime + 1 < k.openTime)
                                throw Exception("inputCandlesticks has a gap in sequence:\n${currentCandlestick}\n${k}")
                        }

                        if (
                            (currentCandlestick!!.closeTime + 1) % outputKlineInterval.toMillis() == 0L
                            || currentCandlestick!!.closeTime % outputKlineInterval.toMillis() == 0L
                        )
                            closeCurrentCandlestick()
                    }
                }

                prevKline = it.let { k ->
                    Candlestick(
                        openTime = k.openTime,
                        closeTime = k.closeTime,
                        open = k.open.round(),
                        high = k.high.round(),
                        low = k.low.round(),
                        close = k.close.round(),
                        volume = k.volume.round()
                    )
                }
            }
    }

    fun closeCurrentCandlestick() {
        candlesticks.add(currentCandlestick!!)
        currentCandlestick = null
    }

    fun getCandlesticks(): List<Candlestick> = candlesticks
}