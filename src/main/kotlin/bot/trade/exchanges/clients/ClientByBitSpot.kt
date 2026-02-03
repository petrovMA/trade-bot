package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.StreamByBitFuturesImpl
import java.util.concurrent.BlockingQueue

class ClientByBitSpot(api: String? = null, sec: String? = null) : ClientByBitBase(api, sec) {

    override fun getCategory(): String = "spot"

    override fun getPositionIdx(positionSide: DIRECTION?): Int = 0

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamByBitFuturesImpl(
            pair = pair,
            queue = queue,
            sec = sec,
            api = api,
            isFuture = false
        )

    override fun toString(): String = "BYBIT_SPOT"
}
