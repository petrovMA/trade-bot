package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.StreamByBitFuturesImpl
import java.util.concurrent.BlockingQueue

class ClientByBitFutures(api: String? = null, sec: String? = null) : ClientByBitBase(api, sec), ClientFutures {

    override fun getCategory(): String = "linear"

    override fun getPositionIdx(positionSide: DIRECTION?): Int = when (positionSide) {
        DIRECTION.LONG -> 1
        DIRECTION.SHORT -> 2
        else -> 0
    }

    init {
        // Initialize Hedge Mode (mode 3) for perpetual futures
        try {
            switchMode(category = "linear", mode = 3, coin = "USDT")
            log.info("ByBit Futures: Hedge mode (mode 3) initialized successfully")
        } catch (e: Exception) {
            log.warn("ByBit Futures: Failed to initialize hedge mode, may already be set", e)
        }
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamByBitFuturesImpl(
            pair = pair,
            queue = queue,
            sec = sec,
            api = api,
            isFuture = true
        )

    override fun getPositions(pair: TradePair): List<Position> =
        client.getPositionsList(
            category = "linear",
            symbol = pair.first + pair.second
        )
            .list
            .map { Position(it) }

    override fun switchMode(category: String, mode: Int, pair: TradePair?, coin: String?) =
        client.switchMode(category, mode, pair?.run { first + second }, coin)

    override fun toString(): String = "BYBIT_FUTURES"
}
