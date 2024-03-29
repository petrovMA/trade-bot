package bot.trade.exchanges.libs

import bot.trade.exchanges.clients.Candlestick
import bot.trade.libs.ListLimit
import bot.trade.libs.toZonedTime
import org.ta4j.core.BaseBar
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

    private val bars = ListLimit<BaseBar>(size)

    private var currentCandlestick: Candlestick? = null
    private var lastCandlestick: Candlestick? = null
    private var prevKline: Candlestick? = null

    fun addCandlesticks(vararg inputCandlesticks: Candlestick): Boolean {
        var isNewKline = false

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

                        if (currentCandlestick!!.closeTime % outputKlineInterval.toMillis() == 0L) {
                            closeCurrentCandlestick()
                            isNewKline = true
                        } else if ((currentCandlestick!!.closeTime + 1) % outputKlineInterval.toMillis() == 0L) {
                            currentCandlestick = Candlestick(
                                openTime = currentCandlestick!!.openTime,
                                closeTime = currentCandlestick!!.closeTime + 1L,
                                open = currentCandlestick!!.open,
                                high = currentCandlestick!!.high,
                                low = currentCandlestick!!.low,
                                close = currentCandlestick!!.close,
                                volume = currentCandlestick!!.volume
                            )
                            closeCurrentCandlestick()
                            isNewKline = true
                        }
                    }
                }

                prevKline = it
            }

        return isNewKline
    }

    fun closeCurrentCandlestick() {
        currentCandlestick?.let {
            if (lastCandlestick == null || lastCandlestick!!.openTime < it.openTime) {
                bars.add(
                    BaseBar(
                        outputKlineInterval,
                        it.closeTime.toZonedTime(),
                        it.open,
                        it.high,
                        it.low,
                        it.close,
                        it.volume,
                        it.close * it.volume
                    )
                )

                lastCandlestick = it
            }
        }
        currentCandlestick = null
    }

    fun getBars(): List<BaseBar> = bars
}